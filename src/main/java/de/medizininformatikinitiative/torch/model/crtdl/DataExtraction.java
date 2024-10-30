package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DataExtraction(

        @JsonProperty(value ="attributeGroups", required = true)
        List<AttributeGroup> attributeGroups
) {}
