package de.medizininformatikinitiative.flare.model.fhir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Optional;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Reference(String reference) {

    public Optional<String> id() {
        return reference == null ? Optional.empty() : Optional.of(reference.substring(reference.indexOf('/') + 1));
    }
}
