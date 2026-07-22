package de.medizininformatikinitiative.torch.model.extraction;

import java.util.Objects;

/**
 * Represents an unresolved {@code Reference.identifier} (a logical/business-identifier reference).
 * <p>
 * Unlike {@link ExtractionId}, no {@code resourceType}/{@code id} is known yet — resolving one requires
 * a search against the configured FHIR source.
 * </p>
 *
 * @param system the identifier system, may be {@code null} if the source identifier has none
 * @param value  the identifier value
 */
public record IdentifierReference(String system, String value) {

    public IdentifierReference {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }
}
