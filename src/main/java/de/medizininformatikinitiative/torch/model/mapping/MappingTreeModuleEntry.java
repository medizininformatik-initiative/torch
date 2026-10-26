package de.medizininformatikinitiative.torch.model.mapping;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record MappingTreeModuleEntry(String key, List<String> children) {
    @JsonCreator
    static MappingTreeModuleEntry fromJson(@JsonProperty("key") String key,
                                           @JsonProperty("children") List<String> children) {
        return new MappingTreeModuleEntry(key, children);
    }
}
