package de.medizininformatikinitiative.torch.model.crtdl.annotated;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnnotatedDataExtraction(@JsonProperty List<AnnotatedAttributeGroup> attributeGroups) {

    public AnnotatedDataExtraction {
        attributeGroups = List.copyOf(attributeGroups);
    }
}
