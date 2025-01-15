package de.medizininformatikinitiative.torch.model;

import de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup;

import java.util.List;
import java.util.Map;


public record ProcessedGroups(List<AttributeGroup> firstPass, List<AttributeGroup> secondPass,
                              Map<String, AttributeGroup> groups) {
    public ProcessedGroups(List<AttributeGroup> firstPass, List<AttributeGroup> secondPass, Map<String, AttributeGroup> groups) {
        this.firstPass = List.copyOf(firstPass); // Immutable lists
        this.secondPass = List.copyOf(secondPass);
        this.groups = Map.copyOf(groups);       // Immutable map
    }
}
