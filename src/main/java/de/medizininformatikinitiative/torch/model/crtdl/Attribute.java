package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Attribute(

        @JsonProperty("attributeRef")
        String attributeRef,

        @JsonProperty("mustHave")
        boolean mustHave
) {}