package de.medizininformatikinitiative.torch.model.management;

import java.util.Objects;

public record ReferenceGroup(String resourceId, String groupId) {

    public ReferenceGroup {
        Objects.requireNonNull(resourceId);
        Objects.requireNonNull(groupId);
    }
}
