package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import static java.util.Objects.requireNonNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Attribute(@JsonProperty(required = true) String attributeRef,
                        @JsonProperty(required = true) boolean mustHave,
                        List<String> linkedGroups) {

    public Attribute {
        requireNonNull(attributeRef);
        if (attributeRef.isEmpty()) {
            throw new IllegalArgumentException("attributeRef must not be empty");
        }
        linkedGroups = linkedGroups == null ? List.of() : List.copyOf(linkedGroups);
    }

    /**
     * Primary constructor for non reference attributes
     */
    public Attribute(String attributeRef,
                     boolean mustHave
    ) {
        this(attributeRef, mustHave, List.of()); // Default value for includeReferenceOnly
    }

}
