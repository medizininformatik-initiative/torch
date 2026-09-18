package de.medizininformatikinitiative.torch.consent;

/**
 * The outcome of checking a single resource against a patient's consent periods in {@link ConsentValidator}.
 */
public enum ConsentCheckOutcome {
    /** The resource type has no entry in the type-to-consent mapping. */
    TYPE_NOT_MAPPED,
    /** The resource type maps to no date field; consent is automatically granted. */
    NO_DATE_FIELD,
    /** A date value on the resource fell inside a consent period. */
    IN_PERIOD,
    /** The mapped date field yielded no usable date value at all. */
    NO_DATE_VALUE,
    /** Date values were found, but none fell inside any consent period. */
    OUTSIDE_PERIODS,
    /** The resource's patient ID resolved, but the batch has no tracked {@code PatientResourceBundle} for it. */
    NO_PATIENT_BUNDLE;

    /**
     * Whether this outcome means the resource complies with consent.
     */
    public boolean included() {
        return this == NO_DATE_FIELD || this == IN_PERIOD;
    }
}
