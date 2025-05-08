package de.medizininformatikinitiative.torch.model.management;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record ResourceGroup(String resourceId, String groupId) {

    public ResourceGroup {
        Objects.requireNonNull(resourceId);
        Objects.requireNonNull(groupId);
    }

    public static List<ResourceGroup> fromList(Set<String> groups, String resourceId) {
        return groups.stream().map(group -> new ResourceGroup(resourceId, group)).collect(Collectors.toList());
    }
}
