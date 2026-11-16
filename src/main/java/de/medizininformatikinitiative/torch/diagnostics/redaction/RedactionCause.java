package de.medizininformatikinitiative.torch.diagnostics.redaction;

import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

/**
 * The innermost child whose failure made a required element unmaskable.
 *
 * @param elementId   element id of the child that failed, e.g. {@code Questionnaire.useContext.code}
 * @param elementType FHIR type of that child
 * @param reason      why that child could not be masked; never {@link RedactionViolationReason#UNMASKABLE_REQUIRED_CHILD}
 */
public record RedactionCause(@JsonProperty String elementId, @JsonProperty String elementType,
                             @JsonProperty RedactionViolationReason reason) {

    public RedactionCause {
        requireNonNull(elementId);
        requireNonNull(elementType);
        requireNonNull(reason);
    }

    @Override
    public String toString() {
        return elementId + " (" + elementType + ")";
    }
}
