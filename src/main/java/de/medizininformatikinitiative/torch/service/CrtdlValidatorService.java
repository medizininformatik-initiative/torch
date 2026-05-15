package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.exceptions.ConsentFormatException;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.consent.ConsentCodeConfig;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedDataExtraction;
import de.medizininformatikinitiative.torch.model.management.TermCode;
import de.medizininformatikinitiative.torch.util.CompiledStructureDefinition;
import de.medizininformatikinitiative.torch.util.FhirPathBuilder;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
public class CrtdlValidatorService {
    private static final Logger logger = LoggerFactory.getLogger(CrtdlValidatorService.class);

    private final StructureDefinitionHandler profileHandler;
    private final StandardAttributeGenerator attributeGenerator;
    private final CrtdlConsentValidator crtdlConsentValidator = new CrtdlConsentValidator();
    private final ConsentCodeConfig consentCodeConfig;
    private final FhirPathBuilder fhirPathBuilder;
    private final FhirContext fhirContext;

    public CrtdlValidatorService(StructureDefinitionHandler profileHandler, StandardAttributeGenerator attributeGenerator, ConsentCodeConfig consentCodeConfig, FhirPathBuilder fhirPathBuilder, FhirContext fhirContext) {
        this.profileHandler = profileHandler;
        this.attributeGenerator = attributeGenerator;
        this.consentCodeConfig = consentCodeConfig;
        this.fhirPathBuilder = fhirPathBuilder;
        this.fhirContext = fhirContext;
    }

    /**
     * Validates crtdl and modifies the attribute allGroups by adding standard attributes and modifiers.
     *
     * @param crtdl the Crtdl to be validated.
     * @return the validated Crtdl or an error signal with ValidationException if a profile is unknown.
     */
    public AnnotatedCrtdl validateAndAnnotate(Crtdl crtdl) throws ValidationException, ConsentFormatException {
        Optional<Set<TermCode>> consentCodes = crtdlConsentValidator.extractConsentCodes(crtdl);
        if (consentCodes.isPresent()) {
            consentCodeConfig.validateCodeCoOccurrence(consentCodes.get());
        }
        List<AnnotatedAttributeGroup> annotatedAttributeGroups = new ArrayList<>();
        Set<String> linkedGroups = new HashSet<>();
        Set<String> successfullyAnnotatedGroups = new HashSet<>();
        boolean exactlyOnePatientGroup = false;
        String patientAttributeGroupId = "";

        for (AttributeGroup attributeGroup : crtdl.dataExtraction().attributeGroups()) {
            CompiledStructureDefinition definition = profileHandler.getDefinition(attributeGroup.groupReference())
                    .orElseThrow(() -> new ValidationException("Unknown Profile: " + attributeGroup.groupReference()));
            Class<?> implementingClass = fhirContext.getResourceDefinition(definition.type()).getImplementingClass();
            if (!DomainResource.class.isAssignableFrom(implementingClass)) {
                throw new ValidationException("Profile " + attributeGroup.groupReference() +
                        " maps to resource type " + definition.type() +
                        ", which is not a DomainResource. Only DomainResource types are supported.");
            }
            if (Objects.equals(definition.type(), "Patient")) {
                if (exactlyOnePatientGroup) {
                    throw new ValidationException(" More than one Patient Attribute Group");
                } else {
                    exactlyOnePatientGroup = true;
                    patientAttributeGroupId = attributeGroup.id();
                    logger.debug("Found Patient Attribute Group {}", patientAttributeGroupId);
                }
            }
        }
        if (!exactlyOnePatientGroup) {
            throw new ValidationException("No Patient Attribute Group");
        }

        for (AttributeGroup attributeGroup : crtdl.dataExtraction().attributeGroups()) {
            CompiledStructureDefinition definition = profileHandler.getDefinition(attributeGroup.groupReference())
                    .orElseThrow(() -> new ValidationException("Unknown Profile: " + attributeGroup.groupReference()));
            for (Attribute attribute : attributeGroup.attributes()) {
                linkedGroups.addAll(attribute.linkedGroups());
            }
            annotatedAttributeGroups.add(annotateGroup(attributeGroup, definition, patientAttributeGroupId));
            successfullyAnnotatedGroups.add(attributeGroup.id());


        }

        linkedGroups.removeAll(successfullyAnnotatedGroups);
        if (!linkedGroups.isEmpty()) {
            throw new ValidationException("Missing defintion for linked groups: " + linkedGroups);
        }
        return new AnnotatedCrtdl(crtdl.cohortDefinition(), new AnnotatedDataExtraction(annotatedAttributeGroups), consentCodes);
    }

    private AnnotatedAttributeGroup annotateGroup(AttributeGroup attributeGroup, CompiledStructureDefinition
            definition, String patientGroupId) throws ValidationException {
        Set<String> standardRefs = attributeGenerator.standardAttributeRefs(definition.type(), definition);
        Set<String> seen = new HashSet<>();
        List<AnnotatedAttribute> annotatedAttributes = new ArrayList<>();
        Set<String> processedRefs = new HashSet<>();

        for (Attribute attribute : attributeGroup.attributes()) {
            if (!seen.add(attribute.attributeRef())) {
                throw new ValidationException("Duplicate attribute " + attribute.attributeRef() + " in group " + attributeGroup.id());
            }
            if (standardRefs.contains(attribute.attributeRef())) {
                if (attribute.mustHave()) {
                    throw new ValidationException("Standard attribute " + attribute.attributeRef() + " in group " + attributeGroup.id() + " cannot be declared with mustHave: true");
                }
                continue;
            }

            Optional<ElementDefinition> elementDefinition = definition.elementDefinitionById(attribute.attributeRef());
            if (elementDefinition.isEmpty()) {
                throw new ValidationException("Unknown Attribute " + attribute.attributeRef() + " in group " + attributeGroup.id());
            }

            if (elementDefinition.get().hasType()) {
                if (!elementDefinition.get().getType("Reference").isEmpty() && elementDefinition.get().getType().size() == 1 && attribute.linkedGroups().isEmpty()) {
                    throw new ValidationException("Reference Attribute " + attribute.attributeRef() + " without linked Groups in group " + attributeGroup.id());
                }
            } else {
                throw new ValidationException("Typeless Attribute " + attribute.attributeRef() + " in group " + attributeGroup.id());
            }

            String[] fhirTerser;
            try {
                fhirTerser = fhirPathBuilder.resolve(attribute.attributeRef(), definition);
            } catch (FHIRException e) {
                throw new ValidationException("Cannot resolve FHIR path for attribute " + attribute.attributeRef() + ": " + e.getMessage());
            }
            annotatedAttributes.add(new AnnotatedAttribute(attribute.attributeRef(), fhirTerser[0], attribute.mustHave(), attribute.linkedGroups()));
            processedRefs.add(attribute.attributeRef());
        }

        annotatedAttributes.addAll(sliceDiscriminatorAttributes(attributeGroup.attributes(), definition, processedRefs));

        return attributeGenerator
                .generate(attributeGroup, patientGroupId)
                .addAttributes(annotatedAttributes);
    }

    /**
     * For each attribute that references a sub-element of a slice (e.g. {@code Patient.address:Strassenanschrift.postalCode}),
     * returns additional non-mustHave attributes for the slice's discriminator fields (e.g. {@code Patient.address:Strassenanschrift.type}).
     * <p>
     * Without these, the copy step omits the discriminator field, and the subsequent redaction step cannot identify
     * which slice the partially-copied element belongs to — causing the entire element to be wiped.
     */
    private List<AnnotatedAttribute> sliceDiscriminatorAttributes(List<Attribute> attributes, CompiledStructureDefinition definition, Set<String> existingRefs) {
        List<AnnotatedAttribute> result = new ArrayList<>();

        for (Attribute attribute : attributes) {
            String ref = attribute.attributeRef();
            if (!ref.contains(":")) continue;

            String[] parts = ref.split("\\.");
            StringBuilder currentId = new StringBuilder();

            for (int i = 0; i < parts.length - 1; i++) {
                if (i > 0) currentId.append(".");
                currentId.append(parts[i]);

                if (!parts[i].contains(":")) continue;

                String sliceId = currentId.toString();
                String parentId = sliceId.substring(0, sliceId.indexOf(":"));

                Optional<ElementDefinition> parentDef = definition.elementDefinitionById(parentId);
                if (parentDef.isEmpty() || !parentDef.get().hasSlicing()) continue;

                for (ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent disc : parentDef.get().getSlicing().getDiscriminator()) {
                    for (String discFieldId : discriminatorFieldIds(sliceId, disc, definition)) {
                        if (!existingRefs.add(discFieldId)) continue;
                        try {
                            String[] fhirTerser = fhirPathBuilder.resolve(discFieldId, definition);
                            result.add(new AnnotatedAttribute(discFieldId, fhirTerser[0], false));
                        } catch (Exception e) {
                            logger.debug("Could not add discriminator attribute {}: {}", discFieldId, e.getMessage());
                        }
                    }
                }
            }
        }

        return result;
    }

    private List<String> discriminatorFieldIds(String sliceId, ElementDefinition.ElementDefinitionSlicingDiscriminatorComponent discriminator, CompiledStructureDefinition definition) {
        String path = discriminator.getPath();

        if ("$this".equals(path)) {
            return definition.elementDefinitionById(sliceId)
                    .filter(ElementDefinition::hasFixedOrPattern)
                    .map(slice -> slice.getFixedOrPattern().children().stream()
                            .filter(Property::hasValues)
                            .map(child -> sliceId + "." + child.getName())
                            .toList())
                    .orElse(List.of());
        }

        return List.of(sliceId + "." + path);
    }
}

