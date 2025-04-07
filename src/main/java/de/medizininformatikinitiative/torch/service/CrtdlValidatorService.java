package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedCrtdl;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedDataExtraction;
import de.medizininformatikinitiative.torch.util.FhirPathBuilder;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CrtdlValidatorService {

    private final StructureDefinitionHandler profileHandler;

    private final CompartmentManager compartmentManager;
    private final List<String> standardPatientProfiles;
    private final List<String> standardPatientGroupNames;


    public CrtdlValidatorService(StructureDefinitionHandler profileHandler, CompartmentManager compartmentManager, List<String> standardPatientProfiles) throws IOException {
        this.profileHandler = profileHandler;
        this.compartmentManager = compartmentManager;
        assert (standardPatientProfiles != null);
        standardPatientProfiles.forEach(profile -> {
                    if (!profileHandler.known(profile)) {
                        try {
                            throw new ValidationException("Profile " + profile + " not known in torch");
                        } catch (ValidationException e) {
                            throw new RuntimeException(e);
                        }
                    }

                }
        );
        this.standardPatientGroupNames = standardPatientProfiles.stream()
                .map(s -> String.valueOf(s.hashCode()))  // or Integer.toHexString(s.hashCode())
                .collect(Collectors.toList());
        this.standardPatientProfiles = standardPatientProfiles;

    }

    /**
     * Validates crtdl and modifies the attribute allGroups by adding standard attributes and modifiers.
     *
     * @param crtdl the Crtdl to be validated.
     * @return the validated Crtdl or an error signal with ValidationException if a profile is unknown.
     */
    public AnnotatedCrtdl validate(Crtdl crtdl) throws ValidationException {
        List<AnnotatedAttributeGroup> annotatedAttributeGroups = new ArrayList<>();
        Set<String> linkedGroups = new HashSet<>();
        Set<String> successfullyAnnotatedGroups = new HashSet<>();


        for (AttributeGroup attributeGroup : crtdl.dataExtraction().attributeGroups()) {
            StructureDefinition definition = profileHandler.getDefinition(attributeGroup.groupReference());
            if (definition != null) {
                for (Attribute attribute : attributeGroup.attributes()) {
                    linkedGroups.addAll(attribute.linkedGroups());
                }
                annotatedAttributeGroups.add(annotateGroup(attributeGroup, definition));
                successfullyAnnotatedGroups.add(attributeGroup.id());


            } else {
                throw new ValidationException("Unknown Profile: " + attributeGroup.groupReference());
            }
        }

        linkedGroups.removeAll(successfullyAnnotatedGroups);
        if (!linkedGroups.isEmpty()) {
            throw new ValidationException("Missing defintion for linked groups: " + linkedGroups);
        }

        standardPatientProfiles.forEach(profile -> {
            AnnotatedAttributeGroup standardGroup = StandardAttributeGenerator.generate(new AttributeGroup(String.valueOf(profile.hashCode()), profile, List.of(), List.of()), "Patient", compartmentManager, standardPatientProfiles);
            annotatedAttributeGroups.add(standardGroup);
        });


        return new AnnotatedCrtdl(crtdl.cohortDefinition(), new AnnotatedDataExtraction(annotatedAttributeGroups));
    }

    private AnnotatedAttributeGroup annotateGroup(AttributeGroup attributeGroup, StructureDefinition definition) throws ValidationException {
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

        AnnotatedAttributeGroup standardGroup = StandardAttributeGenerator.generate(attributeGroup, definition.getType(), compartmentManager, standardPatientGroupNames);

        return standardGroup.addAttributes(annotatedAttributes);
    }

}

