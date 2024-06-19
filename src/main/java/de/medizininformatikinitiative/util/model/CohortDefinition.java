package de.medizininformatikinitiative.util.CRTDL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.util.CRTDL.DataExtraction;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CohortDefinition {
    @JsonProperty("version")
    private String version;

    @JsonProperty("display")
    private String display;

    @JsonProperty("dataExtraction")
    private DataExtraction dataExtraction;

    // Getters and Setters
}
