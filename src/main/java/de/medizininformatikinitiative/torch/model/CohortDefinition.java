package de.medizininformatikinitiative.torch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.flare.model.sq.StructuredQuery;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CohortDefinition {

    // No-argument constructor
    public CohortDefinition() {
    }

    @JsonProperty("version")
    private String version;

    @JsonProperty("display")
    private String display;

    @JsonProperty("dataExtraction")
    private DataExtraction dataExtraction;

    @JsonProperty("cohortDefinition")
    private StructuredQuery structuredQuery;

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

    public void setDataExtraction(DataExtraction dataExtraction) {
        this.dataExtraction = dataExtraction;
    }
}
