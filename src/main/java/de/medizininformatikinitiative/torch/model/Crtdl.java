package de.medizininformatikinitiative.torch.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Crtdl(

        @JsonProperty("version")
        String version,

        @JsonProperty("display")
        String display,

        @JsonProperty("cohortDefinition")
        JsonNode cohortDefinition,

        @JsonProperty("dataExtraction")
        DataExtraction dataExtraction,

        @JsonIgnore
        String sqString
) {
    private static final Logger logger = LoggerFactory.getLogger(Crtdl.class);

    @JsonCreator
    public Crtdl(
            @JsonProperty("version") String version,
            @JsonProperty("display") String display,
            @JsonProperty("cohortDefinition") JsonNode cohortDefinition,
            @JsonProperty("dataExtraction") DataExtraction dataExtraction,
            @JsonProperty("sqString") String sqString
    ) {
        this.version = version;
        this.display = display;
        this.cohortDefinition = cohortDefinition;
        this.dataExtraction = dataExtraction;
        this.sqString = sqString;
    }

    public String getResourceType() {
        return dataExtraction.attributeGroups().get(0).attributes().get(0).attributeRef().split("\\.")[0];
    }

    public String getConsentKey() {
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
        return "";
    }
}
