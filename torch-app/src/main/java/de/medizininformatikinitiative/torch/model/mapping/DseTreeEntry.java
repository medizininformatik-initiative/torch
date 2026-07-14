package de.medizininformatikinitiative.torch.model.mapping;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DseTreeEntry(String key, List<String> children) {

    @JsonCreator
    static DseTreeEntry fromJson(@JsonProperty("key") String key,
                                 @JsonProperty("children") List<String> children) {
        return new DseTreeEntry(key, children);
    }
}
