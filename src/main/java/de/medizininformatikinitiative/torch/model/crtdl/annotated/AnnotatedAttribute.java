package de.medizininformatikinitiative.torch.model.crtdl.annotated;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A validated and annotated CRTDL attribute ready for extraction.
 *
 * @param attributeRef  the FHIR element ID as declared in the StructureDefinition (e.g. {@code Observation.status}),
 *                      used as the stable identifier for validation and de-duplication
 * @param fhirPath      the generated FHIRPath expression derived from the element ID after resolving any slicing,
 *                      used at extraction time with the FHIR Terser
 * @param mustHave      whether the resource must be retained even when this attribute has no value
 * @param linkedGroups  IDs of attribute groups referenced by this attribute that must also be extracted
 */
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

    /** Constructor for non-reference attributes with no linked groups. */
    public AnnotatedAttribute(String attributeRef,
                              String fhirPath,
                              boolean mustHave
    ) {
        this(attributeRef, fhirPath, mustHave, List.of());
    }
}


