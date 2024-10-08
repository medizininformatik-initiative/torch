package de.medizininformatikinitiative.torch.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataExtraction {

    // No-argument constructor
    public DataExtraction() {
    }

    @JsonProperty("attributeGroups")
    private List<AttributeGroup> attributeGroups;

    // Getters and Setters
    public List<AttributeGroup> getAttributeGroups() {
        return attributeGroups;
    }

    public void setAttributeGroups(List<AttributeGroup> attributeGroups) {
        this.attributeGroups = attributeGroups;
    }
}
