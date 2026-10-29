package de.medizininformatikinitiative.torch.model.consent;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

/**
 * How resources of one type are dated for the consent check, as configured in {@code type_to_consent.json}.
 *
 * <p>Both components are set together, or both are absent for types whose resources have no consent-relevant date
 * and are consented whenever the patient has consent.
 *
 * @param dateField       FHIRPath of the date compared against the consent periods, e.g. {@code Condition.recordedDate}
 * @param dateSearchParam the FHIR search param that searches {@code dateField}, e.g. {@code recorded-date}
 */
public record ConsentDateMapping(@JsonProperty("dateField") @Nullable String dateField,
                                 @JsonProperty("dateSearchParam") @Nullable String dateSearchParam) {

    public ConsentDateMapping {
        if ((dateField == null) != (dateSearchParam == null)) {
            throw new IllegalArgumentException("dateField and dateSearchParam have to be set together, got "
                    + dateField + " and " + dateSearchParam);
        }
    }

    public boolean hasDate() {
        return dateField != null;
    }
}
