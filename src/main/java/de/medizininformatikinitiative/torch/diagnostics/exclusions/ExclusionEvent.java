package de.medizininformatikinitiative.torch.diagnostics.exclusions;

/**
 * An Exclusion Event records a single moment when an instance (resource or patient) is excluded from further processing.
 */
public sealed interface ExclusionEvent permits PatientExclusionEvent, ResourceExclusionEvent {

    /**
     * Converts this exclusion event to a CSV row.
     *
     * @return the row created from this exclusion event as an ordered array of strings, each being a column element
     */
    String[] toCsvElements();

}
