package de.medizininformatikinitiative.torch.model.crtdl.annotated;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnnotatedDataExtraction(List<AnnotatedAttributeGroup> attributeGroups) {

    public AnnotatedDataExtraction {
        attributeGroups = List.copyOf(attributeGroups);
    }
}
