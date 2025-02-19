package de.medizininformatikinitiative.torch.model;

import org.hl7.fhir.r4.model.DomainResource;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/*
Map Group auf Attribute
 */
public record ResourceGroupWrapper(DomainResource resource, Set<String> groupSet,
                                   List<ReferenceWrapper> referencedBy, List<String> referencing) {

    public ResourceGroupWrapper {
        Objects.requireNonNull(resource);
        groupSet = Set.copyOf(groupSet);
        referencedBy = List.copyOf(referencedBy);
        referencing = List.copyOf(referencing);
    }

    public ResourceGroupWrapper(DomainResource resource, Set<String> groupSet) {
        this(resource, groupSet, List.of(), List.of());
    }


    public ResourceGroupWrapper addGroups(Set<String> newGroups) {
        System.out.println("addGroups called");

        System.out.println("resource: " + resource);
        System.out.println("newGroups: " + newGroups);
        System.out.println("groupSet: " + groupSet);

        Set<String> updatedGroups = new HashSet<>(groupSet);
        updatedGroups.addAll(newGroups);

        ResourceGroupWrapper wrapper = new ResourceGroupWrapper(resource, updatedGroups, referencedBy, referencing);
        System.out.println("Returning new ResourceGroupWrapper: " + wrapper);

        return wrapper;
    }


    public ResourceGroupWrapper removeGroups(Set<String> groups) {
        HashSet<String> updatedGroups = new HashSet<>(groupSet);
        updatedGroups.removeAll(groups);
        return new ResourceGroupWrapper(resource, updatedGroups, referencedBy, referencing);
    }
}
