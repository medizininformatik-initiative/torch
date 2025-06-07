package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedDataExtraction;
import de.medizininformatikinitiative.torch.model.mapping.ConsentKey;
import de.medizininformatikinitiative.torch.util.FhirPathBuilder;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public class CrtdlValidatorService {
    private static final Logger logger = LoggerFactory.getLogger(CrtdlValidatorService.class);

    private final StructureDefinitionHandler profileHandler;

    private final StandardAttributeGenerator attributeGenerator;
    private final FilterService filterService;


    public CrtdlValidatorService(StructureDefinitionHandler profileHandler, StandardAttributeGenerator attributeGenerator, FilterService filterService) {
        this.profileHandler = profileHandler;
        this.attributeGenerator = attributeGenerator;
        this.filterService = filterService;
    }

    /**
     * Validates crtdl and modifies the attribute allGroups by adding standard attributes and modifiers.
     *
     * @param crtdl the Crtdl to be validated.
     * @return the validated Crtdl or an error signal with ValidationException if a profile is unknown.
     */
    public AnnotatedCrtdl validate(Crtdl crtdl) throws ValidationException {
        Optional<ConsentKey> crtdlKey;
        try {
            crtdlKey = crtdl.consentKey();
        } catch (ValidationException e) {
            throw new ValidationException("No valid Consent Key Found");
        }
        List<AnnotatedAttributeGroup> annotatedAttributeGroups = new ArrayList<>();
        Set<String> linkedGroups = new HashSet<>();
        Set<String> successfullyAnnotatedGroups = new HashSet<>();
        boolean patientGroupFound = false;
        String patientAttributeGroupId = "";

        for (AttributeGroup attributeGroup : crtdl.dataExtraction().attributeGroups()) {
            StructureDefinition definition = profileHandler.getDefinition(attributeGroup.groupReference());
            if (definition != null && "Patient".equals(definition.getType())) {
                if (patientGroupFound) {
                    throw new ValidationException("More than one Patient Attribute Group");
                } else {
                    patientGroupFound = true;
                    patientAttributeGroupId = attributeGroup.id();
                    logger.debug("Found Patient Attribute Group {}", patientAttributeGroupId);
                }
            }
        }
        if (!patientGroupFound) {
            throw new ValidationException("No Patient Attribute Group");
        }

        for (AttributeGroup attributeGroup : crtdl.dataExtraction().attributeGroups()) {
            StructureDefinition definition = profileHandler.getDefinition(attributeGroup.groupReference());
            if (definition != null) {

                for (Attribute attribute : attributeGroup.attributes()) {
                    linkedGroups.addAll(attribute.linkedGroups());
                }
                annotatedAttributeGroups.add(annotateGroup(attributeGroup, definition, patientAttributeGroupId));
                successfullyAnnotatedGroups.add(attributeGroup.id());


            } else {
                throw new ValidationException("Unknown Profile: " + attributeGroup.groupReference());
            }
        }

        linkedGroups.removeAll(successfullyAnnotatedGroups);
        if (!linkedGroups.isEmpty()) {
            throw new ValidationException("Missing definition for linked groups: " + linkedGroups);
        }


        return new AnnotatedCrtdl(crtdl.cohortDefinition(), new AnnotatedDataExtraction(annotatedAttributeGroups), crtdlKey);
    }

    private AnnotatedAttributeGroup annotateGroup(AttributeGroup attributeGroup, StructureDefinition
            definition, String patientGroupId) throws ValidationException {
        List<AnnotatedAttribute> annotatedAttributes = new ArrayList<>();

        for (Attribute attribute : attributeGroup.attributes()) {
            ElementDefinition elementDefinition = definition.getSnapshot().getElementById(attribute.attributeRef());
            if (elementDefinition == null) {
                throw new ValidationException("Unknown Attribute " + attribute.attributeRef() + " in group " + attributeGroup.id());
            }

            if (elementDefinition.hasType()) {
                if (!elementDefinition.getType("Reference").isEmpty() && elementDefinition.getType().size() == 1 && attribute.linkedGroups().isEmpty()) {
                    throw new ValidationException("Reference Attribute " + attribute.attributeRef() + " without linked Groups in group " + attributeGroup.id());
                }
            } else {
                throw new ValidationException("Typeless Attribute " + attribute.attributeRef() + " in group " + attributeGroup.id());
            }

            String[] fhirTerser = FhirPathBuilder.handleSlicingForFhirPath(attribute.attributeRef(), definition.getSnapshot());
            annotatedAttributes.add(new AnnotatedAttribute(attribute.attributeRef(), fhirTerser[0], fhirTerser[1], attribute.mustHave(), attribute.linkedGroups()));
        }

        AnnotatedAttributeGroup standardGroup = attributeGenerator.generate(attributeGroup, patientGroupId);
        Predicate<Resource> filter = filterService.compileFilter(attributeGroup.filter(), definition.getType());
        return standardGroup
                .addAttributes(annotatedAttributes)
                .setCompiledFilter(filter);

    }


}

