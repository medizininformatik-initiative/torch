package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.crtdl.Attribute;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import org.hl7.fhir.r4.model.ElementDefinition;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.io.IOException;

public class CrtdlValidatorService {

    private final StructureDefinitionHandler profileHandler;


    public CrtdlValidatorService(StructureDefinitionHandler profileHandler) throws IOException {
        this.profileHandler = profileHandler;
    }

    /**
     * Validates crtdl and modifies the attribute groups by adding standard attributes and modifier
     *
     * @param crtdl to be validated
     * @return modified crtdl or ValidationException if a profile is unknown.
     * <p>
     */
    public void validate(Crtdl crtdl) throws ValidationException {
        for (AttributeGroup attributeGroup : crtdl.dataExtraction().attributeGroups()) {
            StructureDefinition definition = profileHandler.getDefinition(attributeGroup.groupReference());
            if (definition != null) {
                for (Attribute attribute : attributeGroup.attributes()) {
                    ElementDefinition elementDefinition = definition.getSnapshot().getElementById(attribute.attributeRef());
                    if (elementDefinition == null) {
                        throw new ValidationException("Unknown Attributes in " + attributeGroup.groupReference());
                    }
                }
            } else {
                throw new ValidationException("Unknown Profile: " + attributeGroup.groupReference());
            }
        }
    }

}

