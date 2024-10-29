package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Code(
        @JsonProperty("system") String system,
        @JsonProperty("code") String code,
        @JsonProperty("display") String display
) {

    @JsonCreator
    public Code(
            @JsonProperty("system") String system,
            @JsonProperty("code") String code,
            @JsonProperty("display") String display
    ) {
        this.system = system;
        this.code = code;
        this.display = display;
    }

    // Additional constructor for other uses, if necessary
    public Code(String system, String code) {
        this(system, code, null);
    }

    public String searchParamValue() {
        return system + "|" + code;
    }


}
