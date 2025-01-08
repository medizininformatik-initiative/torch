package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;
import de.medizininformatikinitiative.torch.model.crtdl.Crtdl;
import de.medizininformatikinitiative.torch.model.crtdl.DataExtraction;
import org.hl7.fhir.r4.model.StructureDefinition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CrtdlValidatorService {

    private StructureDefinitionHandler profileHandler;
    private final AttributeGroupPopulator attributeGroupPopulator;


    public CrtdlValidatorService(StructureDefinitionHandler profileHandler) {
        this.profileHandler = profileHandler;
        this.attributeGroupPopulator = new AttributeGroupPopulator(profileHandler);
    }

    /**
     * Validates crtdl and modifies the attribute groups by adding standard attributes and modifiers
     *
     * @param crtdl to be validated
     * @return modified crtdl or illegalArgumentException if a profile is unknown.
     */
    public Crtdl validate(Crtdl crtdl) {
        Set<String> profiles = new HashSet<>();
        profiles.addAll(profileHandler.knownProfiles());
        List<AttributeGroup> groupList = new ArrayList<>();


        crtdl.dataExtraction().attributeGroups().forEach(attributeGroup -> {
            StructureDefinition definition = profileHandler.getDefinition(attributeGroup.groupReference());
            if (definition != null) {
                profiles.remove(attributeGroup.groupReference());
                groupList.add(attributeGroup);
            } else {
                throw new IllegalArgumentException("Unknown Profile: " + attributeGroup.groupReference());
            }
        });
        profiles.forEach(profile -> {
            groupList.add(new AttributeGroup(profile, List.of(), List.of(), true));
        });
        groupList.replaceAll(attributeGroupPopulator::populate);
        return new Crtdl(crtdl.cohortDefinition(), new DataExtraction(groupList));
    }

}
