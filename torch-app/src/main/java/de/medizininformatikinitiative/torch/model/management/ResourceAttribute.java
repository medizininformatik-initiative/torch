package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;

import java.util.Objects;

public record ResourceAttribute(ExtractionId resourceId,
                                AnnotatedAttribute annotatedAttribute) {

    public ResourceAttribute {
        Objects.requireNonNull(annotatedAttribute);
        Objects.requireNonNull(resourceId);
    }


}
