package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;

import java.util.Objects;

public record ResourceAttribute(String resourceId, AnnotatedAttribute annotatedAttribute) {

    public ResourceAttribute {
        Objects.requireNonNull(annotatedAttribute);
        Objects.requireNonNull(resourceId);
    }


}
