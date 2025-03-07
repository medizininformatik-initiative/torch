package de.medizininformatikinitiative.torch.model.management;

import java.util.Objects;

public record ResourceAttributeGroup(String resourceId, String groupId) {

    public ResourceAttributeGroup {
        Objects.requireNonNull(resourceId);
        Objects.requireNonNull(groupId);
    }


}
