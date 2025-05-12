package de.medizininformatikinitiative.torch.model.crtdl.annotated;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

public record AnnotatedCrtdl(JsonNode cohortDefinition, AnnotatedDataExtraction dataExtraction) {

    public AnnotatedCrtdl {
        requireNonNull(cohortDefinition);
        requireNonNull(dataExtraction);
    }

    public Optional<String> consentKey() {
        JsonNode inclusionCriteria = cohortDefinition.get("inclusionCriteria");
        if (inclusionCriteria != null && inclusionCriteria.isArray()) {
            for (JsonNode criteriaGroup : inclusionCriteria) {
                for (JsonNode criteria : criteriaGroup) {
                    JsonNode context = criteria.get("context");
                    if (context != null && "Einwilligung".equals(context.get("code").asText())) {
                        JsonNode termcodes = criteria.get("termCodes");
                        if (termcodes != null && termcodes.isArray()) {
                            JsonNode firstTermcode = termcodes.get(0);
                            if (firstTermcode != null && firstTermcode.has("code")) {
                                return Optional.of(firstTermcode.get("code").asText());
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }
}
