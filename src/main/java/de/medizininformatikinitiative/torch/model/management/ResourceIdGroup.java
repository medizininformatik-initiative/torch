package de.medizininformatikinitiative.torch.model.management;

import java.util.Objects;

public record ResourceIdGroup(String resourceId, String groupId) {

    public ResourceIdGroup {
        Objects.requireNonNull(resourceId);
        Objects.requireNonNull(groupId);
    }
}
