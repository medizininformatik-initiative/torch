package de.medizininformatikinitiative.util.CRTDL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.util.CRTDL.AttributeGroup;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AttributeGroup {
    @JsonProperty("groupReference")
    private String groupReference;

    @JsonProperty("attributes")
    private List<Attribute> attributes;

    @JsonProperty("filter")
    private List<Filter> filter;

    // Getters and Setters
}
