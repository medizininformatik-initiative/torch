package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Crtdl(
        @JsonProperty(required = true)
        JsonNode cohortDefinition,
        @JsonProperty(required = true)
        DataExtraction dataExtraction
) {
    private static final Logger logger = LoggerFactory.getLogger(Crtdl.class);

    public Crtdl {
        requireNonNull(cohortDefinition);
        requireNonNull(dataExtraction);
    }

    public String consentKey() {
        if (cohortDefinition == null) {
            logger.error("cohortDefinition is null");
            return null;
        }

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
                                return firstTermcode.get("code").asText();
                            }
                        }
                    }
                }
            }
        }

        logger.debug("No valid consent key found in cohortDefinition.");
        return "";
    }

}
