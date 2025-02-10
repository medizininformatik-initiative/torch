package de.medizininformatikinitiative.torch.model;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import org.hl7.fhir.r4.model.Resource;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ResourceGroupWrapper(Resource resource, Set<AnnotatedAttributeGroup> groupSet) {

    public ResourceGroupWrapper {
        Objects.requireNonNull(resource);
        groupSet = Set.copyOf(groupSet);
    }

    public ResourceGroupWrapper addGroup(AnnotatedAttributeGroup group) {
        Objects.requireNonNull(group);
        List<AnnotatedAttributeGroup> groups = new java.util.ArrayList<>(groupSet.stream().toList());
        groups.add(group);
        HashSet<AnnotatedAttributeGroup> groupSet = new HashSet<>(groups);
        return new ResourceGroupWrapper(resource, Set.copyOf(groupSet));
    }

    public ResourceGroupWrapper addGroups(Set<AnnotatedAttributeGroup> newGroups) {
        Objects.requireNonNull(newGroups);
        Set<AnnotatedAttributeGroup> updatedGroups = new HashSet<>(groupSet);
        updatedGroups.addAll(newGroups);
        return new ResourceGroupWrapper(resource, Set.copyOf(updatedGroups));
    }
}
