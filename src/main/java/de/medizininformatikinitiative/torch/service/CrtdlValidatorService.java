package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.DataExtraction;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CrtdlValidatorService {

    private StructureDefinitionHandler profileHandler;


    public CrtdlValidatorService(StructureDefinitionHandler profileHandler) {
        this.profileHandler = profileHandler;
    }

    public Crtdl validate(Crtdl crtdl) {
        Set<String> profiles = profileHandler.knownProfiles();
        List<AttributeGroup> groupList = new ArrayList<>();


        crtdl.dataExtraction().attributeGroups().forEach(attributeGroup -> {
            StructureDefinition definition = profileHandler.getDefinition(attributeGroup.groupReference());
            if (definition != null) {
                profiles.remove(attributeGroup.groupReference());
                groupList.add(attributeGroup.addStandardAttributes(definition.getType()));
            } else {
                throw new IllegalArgumentException("Unknown Profile: " + attributeGroup.groupReference());
            }
        });
        profiles.forEach(profile -> {
            //new constructor by profile or attributecreator class
            StructureDefinition definition = profileHandler.getDefinition(profile);
            //get Attributes by Profile

            groupList.add(new AttributeGroup(profile, List.of(), List.of(), true));
        });
        return new Crtdl(crtdl.cohortDefinition(), new DataExtraction(groupList));
    }

}
