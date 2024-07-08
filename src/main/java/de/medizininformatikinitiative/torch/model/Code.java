package de.medizininformatikinitiative.torch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
        String encodedString = "";
        try {
            encodedString = URLEncoder.encode(system + "|" + code, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return encodedString;

    }

    // Getters and Setters
}
