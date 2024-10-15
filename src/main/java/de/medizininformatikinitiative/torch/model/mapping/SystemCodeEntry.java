package de.medizininformatikinitiative.torch.model.mapping;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SystemCodeEntry(String system, String code, Byte contextKey) {

    @JsonCreator
    public static SystemCodeEntry fromJson(@JsonProperty("system") String system, @JsonProperty("code") String code,
                                           @JsonProperty("context-key") String contextKey) {
        return new SystemCodeEntry(system, code, Byte.parseByte(contextKey));
    }
}
