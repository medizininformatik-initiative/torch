package de.medizininformatikinitiative.torch.model.crtdl.annotated;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import de.medizininformatikinitiative.torch.consent.ConsentContext;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnnotatedCrtdl(@JsonProperty JsonNode cohortDefinition,
                             @JsonProperty AnnotatedDataExtraction dataExtraction) implements ConsentContext {

    public AnnotatedCrtdl {
        requireNonNull(cohortDefinition);
        requireNonNull(dataExtraction);
    }
}
