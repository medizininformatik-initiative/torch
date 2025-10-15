package de.medizininformatikinitiative.torch.model.crtdl.annotated;

import com.fasterxml.jackson.databind.JsonNode;
import de.medizininformatikinitiative.torch.model.consent.ConsentCode;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Objects.requireNonNull;

public record AnnotatedCrtdl(JsonNode cohortDefinition, AnnotatedDataExtraction dataExtraction) {

    public AnnotatedCrtdl {
        requireNonNull(cohortDefinition);
        requireNonNull(dataExtraction);
    }

    public Optional<Set<ConsentCode>> consentKey() {
        JsonNode inclusionCriteria = cohortDefinition.get("inclusionCriteria");
        if (inclusionCriteria == null || !inclusionCriteria.isArray()) {
            return Optional.empty();
        }
        Set<ConsentCode> codes = StreamSupport.stream(inclusionCriteria.spliterator(), false)
                .filter(JsonNode::isArray)
                .flatMap(group -> StreamSupport.stream(group.spliterator(), false))
                .filter(criteria -> "Einwilligung".equals(criteria.path("context").path("code").asText(null)))
                .flatMap(criteria -> {
                    JsonNode termCodes = criteria.path("termCodes");
                    if (termCodes.isArray()) {
                        return StreamSupport.stream(termCodes.spliterator(), false)
                                .map(tc -> {
                                    String system = tc.path("system").asText(null);
                                    String code = tc.path("code").asText(null);
                                    if (system != null && code != null) {
                                        return new ConsentCode(system, code);
                                    } else {
                                        return null; // skip nodes missing data
                                    }
                                })
                                .filter(Objects::nonNull);
                    }
                    return Stream.empty();
                })
                .collect(Collectors.toSet());

        return codes.isEmpty() ? Optional.empty() : Optional.of(codes);
    }
}
