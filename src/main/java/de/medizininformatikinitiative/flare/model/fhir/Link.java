package de.medizininformatikinitiative.flare.model.fhir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Link(String relation, String url) {

    public Link {
        Objects.requireNonNull(relation);
        Objects.requireNonNull(url);
    }
}
