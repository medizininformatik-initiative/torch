package de.medizininformatikinitiative.util.CRTDL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataExtraction {
    @JsonProperty("attributeGroups")
    private List<AttributeGroup> attributeGroups;

    // Getters and Setters
}
