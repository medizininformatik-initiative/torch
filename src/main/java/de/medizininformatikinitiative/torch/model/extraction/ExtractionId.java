package de.medizininformatikinitiative.torch.model.extraction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a FHIR relative reference of the form {@code ResourceType/id}.
 * <p>
 * This is a lightweight value object used during extraction and linking
 * to identify resources without relying on absolute URLs.
 * </p>
 *
 * <p>
 * Invariants:
 * <ul>
 *   <li>{@code resourceType} must not be {@code null} or blank</li>
 *   <li>{@code id} must not be {@code null} or blank</li>
 * </ul>
 * </p>
 *
 * @param resourceType the FHIR resource type (e.g. {@code Patient}, {@code Encounter})
 * @param id           the logical resource id
 */
public record ExtractionId(String resourceType, String id) implements Comparable<ExtractionId> {

    public ExtractionId {
        if (resourceType == null) {
            throw new IllegalArgumentException("resourceType must not be null");
        }
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (resourceType.isBlank()) {
            throw new IllegalArgumentException("resourceType must not be blank");
        }
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }

    @Override
    public int compareTo(ExtractionId other) {
        int cmp = resourceType.compareTo(other.resourceType);
        if (cmp != 0) return cmp;
        return id.compareTo(other.id);
    }

    /**
     * Creates an {@link ExtractionId} from a relative reference string.
     *
     * @param relativeUrl a string of the form {@code ResourceType/id}
     * @return parsed {@link ExtractionId}
     * @throws IllegalArgumentException if the input is not a valid relative reference
     *                                  Jackson: used for deserialization (reads from a JSON string).
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ExtractionId fromRelativeUrl(String relativeUrl) {
        Objects.requireNonNull(relativeUrl, "relativeUrl must not be null");

        // Disallow absolute URLs
        if (relativeUrl.contains("://")) {
            throw new IllegalArgumentException(
                    "Absolute references are not supported: '" + relativeUrl + "'"
            );
        }

        // Disallow URNs
        if (relativeUrl.startsWith("urn:")) {
            throw new IllegalArgumentException(
                    "URN references are not supported: '" + relativeUrl + "'"
            );
        }

        // Disallow leading slash
        if (relativeUrl.startsWith("/")) {
            throw new IllegalArgumentException(
                    "Invalid relative reference (leading slash not allowed): '" + relativeUrl + "'"
            );
        }

        // Must contain exactly one slash
        int firstSlash = relativeUrl.indexOf('/');
        if (firstSlash <= 0 || firstSlash == relativeUrl.length() - 1) {
            throw new IllegalArgumentException(
                    "Invalid relative reference: expected 'ResourceType/id' but got '" + relativeUrl + "'"
            );
        }

        if (relativeUrl.indexOf('/', firstSlash + 1) != -1) {
            throw new IllegalArgumentException(
                    "Invalid relative reference: too many path segments in '" + relativeUrl + "'"
            );
        }

        String resourceType = relativeUrl.substring(0, firstSlash);
        String id = relativeUrl.substring(firstSlash + 1);

        if (resourceType.isBlank() || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid relative reference: expected 'ResourceType/id' but got '" + relativeUrl + "'"
            );
        }

        return new ExtractionId(resourceType, id);
    }

    /**
     * Creates a {@link Set} of {@link ExtractionId}s from one or more
     * relative reference strings of the form {@code ResourceType/id}.
     *
     * <pre>
     * ExtractionId.of("Patient/1");
     * ExtractionId.of("Patient/1", "Encounter/2");
     * </pre>
     * <p>
     * The returned set preserves insertion order and removes duplicates.
     *
     * @param relativeUrls one or more strings of the form {@code ResourceType/id}
     * @return a set of parsed {@link ExtractionId}s
     * @throws IllegalArgumentException if any string is not a valid relative reference
     */
    public static Set<ExtractionId> of(String... relativeUrls) {
        Objects.requireNonNull(relativeUrls, "relativeUrls must not be null");

        return Arrays.stream(relativeUrls)
                .map(ExtractionId::fromRelativeUrl)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
    }

    /**
     * Returns the relative FHIR reference string ({@code ResourceType/id}).
     *
     * @return relative reference representation
     * Jackson: used for serialization (writes as a JSON string).
     */
    @JsonValue
    public String toRelativeUrl() {
        return resourceType + "/" + id;
    }

    @Override
    public String toString() {
        return toRelativeUrl();
    }
}
