package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Crtdl(
        @JsonProperty(required = true)
        JsonNode cohortDefinition,
        @JsonProperty(required = true)
        DataExtraction dataExtraction
) {
    public Crtdl {
        requireNonNull(cohortDefinition);
        requireNonNull(dataExtraction);
    }
}
