package de.medizininformatikinitiative.torch.model.crtdl.annotated;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnnotatedAttribute(@JsonProperty String attributeRef,
                                 @JsonProperty String fhirPath,
                                 @JsonProperty boolean mustHave,
                                 @JsonProperty List<String> linkedGroups) {

    public AnnotatedAttribute {
        requireNonNull(attributeRef);
        requireNonNull(fhirPath);
        linkedGroups = linkedGroups == null ? List.of() : List.copyOf(linkedGroups);
    }

    /**
     * Primary constructor for non reference attributes.
     */
    public AnnotatedAttribute(String attributeRef,
                              String fhirPath,
                              boolean mustHave
    ) {
        this(attributeRef, fhirPath, mustHave, List.of()); // Default value for includeReferenceOnly
    }
}


