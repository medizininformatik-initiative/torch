package de.medizininformatikinitiative.torch.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import de.medizininformatikinitiative.torch.ResourceTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Crtdl {

    private static final Logger logger = LoggerFactory.getLogger(Crtdl.class);

    // No-argument constructor
    public Crtdl() {
    }

    @JsonProperty("version")
    private String version;

    @JsonProperty("display")
    private String display;

    @JsonProperty("cohortDefinition")
    private JsonNode cohortDefinition;

    @JsonIgnore
    private String sqString;

    @JsonProperty("dataExtraction")
    private DataExtraction dataExtraction;

    // Getters and Setters
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public DataExtraction getDataExtraction() {
        return dataExtraction;
    }

    public String getResourceType() {
        return dataExtraction.getAttributeGroups().getFirst().getAttributes().getFirst().getAttributeRef().split("\\.")[0];
    }

    public JsonNode getCohortDefinition() {
        return cohortDefinition;
    }

    // Helper method to extract the consent key from the cohortDefinition JSON node
    public String getConsentKey() {
        if (cohortDefinition == null) {
            logger.error("cohortDefinition is null");
            return null;
        }

        // Traverse through the cohortDefinition, looking for "Einwilligung" context and extract the termcode
        JsonNode inclusionCriteria = cohortDefinition.get("inclusionCriteria");
        if (inclusionCriteria != null && inclusionCriteria.isArray()) {
            for (JsonNode criteriaGroup : inclusionCriteria) {
                for (JsonNode criteria : criteriaGroup) {
                    JsonNode context = criteria.get("context");
                    if (context != null && "Einwilligung".equals(context.get("code").asText())) {
                        JsonNode termcodes = criteria.get("termCodes");
                        if (termcodes != null && termcodes.isArray()) {
                            // Assuming only one termcode per "Einwilligung" context
                            JsonNode firstTermcode = termcodes.get(0);
                            if (firstTermcode != null && firstTermcode.has("code")) {
                                return firstTermcode.get("code").asText();
                            }
                        }
                    }
                }
            }
        }

        logger.error("No valid consent key found in cohortDefinition.");
        return "";
    }

}
