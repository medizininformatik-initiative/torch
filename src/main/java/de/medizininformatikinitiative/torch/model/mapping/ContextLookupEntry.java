package de.medizininformatikinitiative.torch.model.mapping;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.numcodex.sq2cql.model.common.TermCode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ContextLookupEntry(TermCode context, String key) {

    @JsonCreator
    public static ContextLookupEntry fromJson(@JsonProperty("context") TermCode context, @JsonProperty("key") String key) {
        return new ContextLookupEntry(context, key);
    }
}
