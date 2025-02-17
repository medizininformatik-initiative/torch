package de.medizininformatikinitiative.torch.model;

import org.hl7.fhir.r4.model.Resource;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ResourceGroupWrapper(Resource resource, Set<String> groupSet,
                                   List<ReferenceWrapper> referencedBy, List<String> referencing) {

    public ResourceGroupWrapper {
        Objects.requireNonNull(resource);
        groupSet = Set.copyOf(groupSet);
        referencedBy = List.copyOf(referencedBy);
        referencing = List.copyOf(referencing);
    }

    public ResourceGroupWrapper(Resource resource, Set<String> groupSet) {
        this(resource, groupSet, List.of(), List.of());
    }


    public ResourceGroupWrapper addGroups(Set<String> newGroups) {
        Objects.requireNonNull(newGroups);
        Set<String> updatedGroups = new HashSet<>(groupSet);
        updatedGroups.addAll(newGroups);
        return new ResourceGroupWrapper(resource, Set.copyOf(updatedGroups), referencedBy, referencing);
    }

    public ResourceGroupWrapper removeGroups(Set<String> groups) {
        HashSet<String> updatedGroups = new HashSet<>(groupSet);
        updatedGroups.removeAll(groups);
        return new ResourceGroupWrapper(resource, updatedGroups, referencedBy, referencing);
    }
}
