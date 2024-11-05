package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Attribute(String attributeRef, boolean mustHave) {

    public Attribute {
        requireNonNull(attributeRef);
    }
}
