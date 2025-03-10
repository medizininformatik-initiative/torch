package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;

import java.util.Objects;

public record ResourceAttributeGroup(String resourceId, AnnotatedAttribute annotatedAttribute, String groupId) {

    public ResourceAttributeGroup {
        Objects.requireNonNull(annotatedAttribute);
        Objects.requireNonNull(resourceId);
        Objects.requireNonNull(groupId);
    }


}
