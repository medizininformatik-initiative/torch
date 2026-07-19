package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;

import java.util.Objects;

public record ResourceGroup(ExtractionId resourceId,
                            String groupId) {

    public ResourceGroup {
        Objects.requireNonNull(resourceId);
        Objects.requireNonNull(groupId);
    }
}
