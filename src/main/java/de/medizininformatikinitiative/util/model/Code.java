package de.medizininformatikinitiative.util.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URLEncoder;

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



    public String getCodeURL(){
        return URLEncoder.encode(system)+"|"+URLEncoder.encode(code);

    }

    // Getters and Setters
}
