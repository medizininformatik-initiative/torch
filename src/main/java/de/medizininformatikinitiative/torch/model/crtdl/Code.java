package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

public record Code(
        @JsonProperty(required = true) String system,
        @JsonProperty(required = true) String code,
        @JsonProperty(required = true) String display,
        String version
) {
    public Code {
        requireNonNull(system);
        requireNonNull(code);
        requireNonNull(display);
    }

    public Code(String system, String code) {
        this(system, code, "", "");
    }

    public String searchParamValue() {
        return system + "|" + code;
    }
}
