package de.medizininformatikinitiative.torch.diagnostics.redaction;

/**
 * Why redaction left a required element absent or invalid.
 */
public enum RedactionViolationReason {
    /**
     * A child the base FHIR specification requires could not be masked, so the element is either left out
     * or kept without that child.
     */
    UNMASKABLE_REQUIRED_CHILD("base-required child could not be masked"),
    /**
     * The element's type cannot be instantiated, so no masked placeholder can be built for it.
     */
    UNRESOLVABLE_TYPE("type cannot be instantiated for a masked placeholder"),
    /**
     * The parent has no setter for the element, so a masked placeholder cannot be attached.
     */
    NO_SETTER("no setter to attach a masked placeholder");

    private final String description;

    RedactionViolationReason(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
