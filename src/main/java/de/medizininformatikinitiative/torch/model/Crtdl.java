package de.medizininformatikinitiative.torch.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import de.medizininformatikinitiative.flare.model.sq.StructuredQuery;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Crtdl {

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
}
