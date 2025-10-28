package de.medizininformatikinitiative.torch.model.crtdl.annotated;

import com.fasterxml.jackson.databind.JsonNode;
import de.medizininformatikinitiative.torch.model.management.TermCode;

import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public record AnnotatedCrtdl(JsonNode cohortDefinition, AnnotatedDataExtraction dataExtraction,
                             Optional<Set<TermCode>> consentCodes) {

    public AnnotatedCrtdl {
        requireNonNull(cohortDefinition);
        requireNonNull(dataExtraction);
    }
}
