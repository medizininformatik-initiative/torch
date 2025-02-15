package de.medizininformatikinitiative.torch.model;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import org.hl7.fhir.r4.model.Resource;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ResourceGroupWrapper(Resource resource, Set<AnnotatedAttributeGroup> groupSet,
                                   List<ReferenceWrapper> references) {

    public ResourceGroupWrapper {
        Objects.requireNonNull(resource);
        groupSet = Set.copyOf(groupSet);
        references = List.copyOf(references);
    }

    public ResourceGroupWrapper(Resource resource, Set<AnnotatedAttributeGroup> groupSet) {
        this(resource, groupSet, List.of());
    }


    public ResourceGroupWrapper addGroups(Set<AnnotatedAttributeGroup> newGroups) {
        Objects.requireNonNull(newGroups);
        Set<AnnotatedAttributeGroup> updatedGroups = new HashSet<>(groupSet);
        updatedGroups.addAll(newGroups);
        return new ResourceGroupWrapper(resource, Set.copyOf(updatedGroups), references);
    }

    public ResourceGroupWrapper removeGroups(Set<AnnotatedAttributeGroup> groups) {
        Set<AnnotatedAttributeGroup> updatedGroups = new HashSet<>(groupSet);
        updatedGroups.removeAll(groups);
        return new ResourceGroupWrapper(resource, updatedGroups, references);

    }

}
