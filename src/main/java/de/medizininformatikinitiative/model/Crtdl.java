package de.medizininformatikinitiative.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


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
    private CohortDefinition cohortDefinition;

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

    public CohortDefinition getCohortDefinition() {
        return cohortDefinition;
    }

    public void setCohortDefinition(CohortDefinition cohortDefinition) {
        this.cohortDefinition = cohortDefinition;
    }

    public String getResourceType() {
        return cohortDefinition.getDataExtraction().getAttributeGroups().get(0).getAttributes().get(0).getAttributeRef().split("\\.")[0];
    }
}
