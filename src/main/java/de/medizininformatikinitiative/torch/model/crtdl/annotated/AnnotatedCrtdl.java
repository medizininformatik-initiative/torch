package de.medizininformatikinitiative.torch.model.crtdl.annotated;

import com.fasterxml.jackson.databind.JsonNode;
import de.medizininformatikinitiative.torch.model.mapping.ConsentKey;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record AnnotatedCrtdl(JsonNode cohortDefinition, AnnotatedDataExtraction dataExtraction,
                             Optional<ConsentKey> consentKey) {

    public AnnotatedCrtdl {
        requireNonNull(cohortDefinition);
        requireNonNull(dataExtraction);
        requireNonNull(consentKey);
    }
}
