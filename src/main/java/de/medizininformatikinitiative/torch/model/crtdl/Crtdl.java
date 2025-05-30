package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import de.medizininformatikinitiative.torch.exceptions.ValidationException;
import de.medizininformatikinitiative.torch.model.mapping.ConsentKey;

import java.util.Optional;

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

    public Optional<ConsentKey> consentKey() throws ValidationException {
        JsonNode inclusionCriteria = cohortDefinition.get("inclusionCriteria");
        if (inclusionCriteria != null && inclusionCriteria.isArray()) {
            for (JsonNode criteriaGroup : inclusionCriteria) {
                for (JsonNode criteria : criteriaGroup) {
                    JsonNode context = criteria.get("context");
                    if (context != null && "Einwilligung".equals(context.get("code").asText())) {
                        JsonNode firstTermcode = criteria.get("termCodes").get(0);
                        if (firstTermcode != null && firstTermcode.has("code")) {
                            String code = firstTermcode.get("code").asText();
                            return Optional.of(ConsentKey.fromString(code));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }
}
