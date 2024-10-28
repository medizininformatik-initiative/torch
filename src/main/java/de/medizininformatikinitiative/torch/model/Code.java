package de.medizininformatikinitiative.torch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Code(

        @JsonProperty("system")
        String system,

        @JsonProperty("code")
        String code,

        @JsonProperty("display")
        String display
) {

    public Code(
            @JsonProperty("system") String system,
            @JsonProperty("code") String code
    ) {
        this(system, code, null);
    }

    public String getCodeURL() {
        try {
            return URLEncoder.encode(system + "|" + code, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}
