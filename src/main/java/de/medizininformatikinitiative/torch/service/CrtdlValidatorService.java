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
import de.medizininformatikinitiative.torch.util.FhirPathBuilder;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CrtdlValidatorService {

    private final StructureDefinitionHandler profileHandler;
    private final FhirPathBuilder fhirPathBuilder;


    public CrtdlValidatorService(StructureDefinitionHandler profileHandler) throws IOException {
        this.profileHandler = profileHandler;
        this.fhirPathBuilder = new FhirPathBuilder();
    }

    /**
     * Validates crtdl and modifies the attribute allGroups by adding standard attributes and modifiers.
     *
     * @param crtdl the Crtdl to be validated.
     * @return the validated Crtdl or an error signal with ValidationException if a profile is unknown.
     */
    public AnnotatedCrtdl validate(Crtdl crtdl) throws ValidationException {
        List<AnnotatedAttributeGroup> annotatedAttributeGroups = new ArrayList<>();
        for (AttributeGroup attributeGroup : crtdl.dataExtraction().attributeGroups()) {
            StructureDefinition definition = profileHandler.getDefinition(attributeGroup.groupReference());
            if (definition != null) {
                annotatedAttributeGroups.add(annotateGroup(attributeGroup, definition));
            } else {
                throw new ValidationException("Unknown Profile: " + attributeGroup.groupReference());
            }
        }
        return new AnnotatedCrtdl(crtdl.cohortDefinition(), new AnnotatedDataExtraction(annotatedAttributeGroups));
    }

    private AnnotatedAttributeGroup annotateGroup(AttributeGroup attributeGroup, StructureDefinition definition) throws ValidationException {
        attributeGroup = StandardAttributeGenerator.generate(attributeGroup, attributeGroup.resourceType());
        List<AnnotatedAttribute> annotatedAttributes = new ArrayList<>();
        for (Attribute attribute : attributeGroup.attributes()) {
            ElementDefinition elementDefinition = definition.getSnapshot().getElementById(attribute.attributeRef());
            if (elementDefinition == null) {
                throw new ValidationException("Unknown Attributes in " + attributeGroup.groupReference());
            }
            String[] fhirTerser = FhirPathBuilder.handleSlicingForFhirPath(fhirPathBuilder, attribute.attributeRef(), definition.getSnapshot());
            annotatedAttributes.add(new AnnotatedAttribute(attribute.attributeRef(), fhirTerser[0], fhirTerser[1], attribute.mustHave(), attribute.linkedGroups()));
        }
        return new AnnotatedAttributeGroup(attributeGroup.id(), attributeGroup.groupReference(), annotatedAttributes, attributeGroup.filter());
    }

}

