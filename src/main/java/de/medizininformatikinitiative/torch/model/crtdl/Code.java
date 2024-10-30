package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


public record Code(
        @JsonProperty(value = "system",required = true) String system,
        @JsonProperty(value = "code", required = true)  String code,
        @JsonProperty(value = "display",required = true) String display
) {


    // Additional constructor for other uses, if necessary
    public Code(String system, String code) {
        this(system, code, null);
    }

    public String searchParamValue() {
        return system + "|" + code;
    }


}
