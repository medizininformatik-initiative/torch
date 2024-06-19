package de.medizininformatikinitiative.util.CRTDL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Attribute {
    @JsonProperty("attributeRef")
    private String attributeRef;

    @JsonProperty("mustHave")
    private boolean mustHave;

    // Getters and Setters
}
