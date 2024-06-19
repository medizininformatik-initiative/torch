package de.medizininformatikinitiative.util.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Code {

    // No-argument constructor
    public Code() {
    }
    @JsonProperty("code")
    private String code;

    @JsonProperty("system")
    private String system;

    @JsonProperty("display")
    private String display;

    // Getters and Setters
}
