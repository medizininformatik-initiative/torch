package de.medizininformatikinitiative.torch.diagnostics.redaction;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Aggregated occurrences of one required element that redaction left absent or invalid, across resources.
 *
 * @param resourceType type of the affected resources
 * @param elementId    element id of the affected element, e.g. {@code Observation.component.value[x]}
 * @param elementType  FHIR type of the affected element
 * @param reason       why the element could not be masked
 * @param cause        for {@link RedactionViolationReason#UNMASKABLE_REQUIRED_CHILD}, the innermost child that failed;
 *                     {@code null} otherwise and then omitted from JSON
 * @param count        number of occurrences
 * @param examples     up to {@link #MAX_EXAMPLES} relative ids of affected resources, e.g. {@code Observation/abc}
 */
public record RedactionViolation(@JsonProperty String resourceType, @JsonProperty String elementId,
                                 @JsonProperty String elementType, @JsonProperty RedactionViolationReason reason,
                                 @JsonProperty @JsonInclude(JsonInclude.Include.NON_NULL) RedactionCause cause,
                                 @JsonProperty long count, @JsonProperty List<String> examples) {

    public static final int MAX_EXAMPLES = 3;

    public RedactionViolation {
        requireNonNull(resourceType);
        requireNonNull(elementId);
        requireNonNull(elementType);
        requireNonNull(reason);
        examples = examples == null ? List.of() : List.copyOf(examples);
    }

    public RedactionViolation(String resourceType, String elementId, String elementType, RedactionViolationReason reason,
                              long count, List<String> examples) {
        this(resourceType, elementId, elementType, reason, null, count, examples);
    }

    /**
     * @return whether {@code other} describes the same element, type, reason and cause
     */
    public boolean sameKey(RedactionViolation other) {
        return resourceType.equals(other.resourceType) && elementId.equals(other.elementId)
                && elementType.equals(other.elementType) && reason == other.reason && Objects.equals(cause, other.cause);
    }

    /**
     * Sums the counts and combines the examples, keeping at most {@link #MAX_EXAMPLES}.
     *
     * @throws IllegalArgumentException if {@code other} has a different key
     */
    public RedactionViolation merge(RedactionViolation other) {
        if (!sameKey(other)) {
            throw new IllegalArgumentException("Cannot merge redaction violations with different keys");
        }
        Set<String> mergedExamples = new LinkedHashSet<>(examples);
        mergedExamples.addAll(other.examples);
        return new RedactionViolation(resourceType, elementId, elementType, reason, cause, count + other.count,
                mergedExamples.stream().limit(MAX_EXAMPLES).toList());
    }

    public String message() {
        return "Redaction produced resources violating required cardinality: " + elementId + " (" + elementType + ") – "
                + reason.description() + (cause == null ? "" : ": " + cause + " – " + cause.reason().description());
    }

    public String diagnostics() {
        return "count=" + count + "; reason=" + reason + (cause == null ? "" : "; cause=" + cause.reason() + " at " + cause)
                + (examples.isEmpty() ? "" : "; examples=" + String.join(", ", examples));
    }
}
