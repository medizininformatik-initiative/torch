package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;

import java.util.Set;

public class CrtdlValidatorService {

    private StructureDefinitionHandler profileHandler;


    public CrtdlValidatorService(StructureDefinitionHandler profileHandler) {
        this.profileHandler = profileHandler;
    }

    public Crtdl validate(Crtdl crtdl) {
        Set<String> profiles = profileHandler.knownProfiles();
        crtdl.dataExtraction().attributeGroups().forEach(attributeGroup -> {
            if (profiles.contains(attributeGroup.groupReference())) {
                profiles.remove(attributeGroup.groupReference());
                //Add standard fields

            } else {
                throw new IllegalArgumentException("Unknown Profile: " + attributeGroup.groupReference());
            }
        });
        profiles.forEach(profile -> {
            //new constructor by profile or attributecreator class
        });
        return crtdl;
    }

}
