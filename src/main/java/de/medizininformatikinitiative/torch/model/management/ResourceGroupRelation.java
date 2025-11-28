package de.medizininformatikinitiative.torch.model.management;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record ResourceGroupRelation(String resourceId, String groupId) {

    public ResourceGroupRelation {
        Objects.requireNonNull(resourceId);
        Objects.requireNonNull(groupId);
    }

    public static List<ResourceGroupRelation> fromList(Set<String> groups, String resourceId) {
        return groups.stream().map(group -> new ResourceGroupRelation(resourceId, group)).collect(Collectors.toList());
    }
}
