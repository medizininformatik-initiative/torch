package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Code(
        @JsonProperty(required = true) String system,
        @JsonProperty(required = true) String code
) {
    public Code {
        requireNonNull(system);
        requireNonNull(code);
    }

    @Override
    public String toString() {
        return system + "|" + code;
    }
}
