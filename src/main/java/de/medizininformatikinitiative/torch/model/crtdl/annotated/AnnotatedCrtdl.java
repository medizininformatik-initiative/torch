package de.medizininformatikinitiative.torch.model.crtdl.annotated;

import com.fasterxml.jackson.databind.JsonNode;
import de.medizininformatikinitiative.torch.model.consent.ConsentCode;
import de.medizininformatikinitiative.torch.util.ConfigUtils;

import java.util.Objects;
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

    /**
     * For a given consent Context, it extracts all single codes belonging to that context defined in the inclusion criteria.
     *
     * @param consentContexts the context under which consent codes are encoded in the inclusion criteria.
     * @return Set of consentCodes to be applied to the data extraction.
     */
    public Set<ConsentCode> consentKey(Set<ConsentCode> consentContexts) {
        JsonNode inclusionCriteria = cohortDefinition.get("inclusionCriteria");
        if (inclusionCriteria == null || !inclusionCriteria.isArray()) {
            return Set.of();
        }
        return StreamSupport.stream(inclusionCriteria.spliterator(), false)
                .filter(JsonNode::isArray)
                .flatMap(group -> StreamSupport.stream(group.spliterator(), false))
                .filter(criteria -> {
                    String system = criteria.path("context").path("system").asText(null);
                    String code = criteria.path("context").path("code").asText(null);
                    if (!ConfigUtils.isNotSet(system) && !ConfigUtils.isNotSet(code)) {
                        return consentContexts.contains(new ConsentCode(system, code));
                    } else {
                        return false; // skip nodes missing data
                    }
                })
                .flatMap(criteria -> {
                    JsonNode termCodes = criteria.path("termCodes");
                    if (termCodes.isArray()) {
                        return StreamSupport.stream(termCodes.spliterator(), false)
                                .map(tc -> {
                                    String system = tc.path("system").asText(null);
                                    String code = tc.path("code").asText(null);
                                    if (!ConfigUtils.isNotSet(system) && !ConfigUtils.isNotSet(code)) {
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
    }
}
