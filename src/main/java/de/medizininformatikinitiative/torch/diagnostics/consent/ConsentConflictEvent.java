package de.medizininformatikinitiative.torch.diagnostics.consent;

import de.medizininformatikinitiative.torch.diagnostics.exclusions.CsvDefinition;

import java.time.LocalDate;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Records a permit and a deny of the same consent code whose periods overlap within one signing event (all
 * {@code Consent} resources sharing a {@code dateTime}). Such an encoding cannot express a valid patient will; the
 * deny wins for the overlap, and this event makes the inconsistency visible to the data provider.
 *
 * @param patientId       the ID of the patient
 * @param dateTime        the {@code Consent.dateTime} of the signing event
 * @param code            the conflicting consent code
 * @param permitConsentId the ID of the {@code Consent} resource carrying the permit
 * @param denyConsentId   the ID of the {@code Consent} resource carrying the deny, possibly the same as the permit's
 * @param overlapStart    the start of the overlap, based on the permit's period before any encounter shift
 * @param overlapEnd      the end of the overlap
 */
public record ConsentConflictEvent(String patientId, String dateTime, String code, String permitConsentId,
                                   String denyConsentId, LocalDate overlapStart, LocalDate overlapEnd) {

    public ConsentConflictEvent {
        requireNonNull(patientId);
        requireNonNull(dateTime);
        requireNonNull(code);
        requireNonNull(permitConsentId);
        requireNonNull(denyConsentId);
        requireNonNull(overlapStart);
        requireNonNull(overlapEnd);
    }

    /**
     * Converts a set of strings read directly from a CSV row to a {@link ConsentConflictEvent}.
     *
     * @param csvRow the elements of the CSV row
     * @return the newly converted {@link ConsentConflictEvent}
     */
    public static ConsentConflictEvent fromCsv(String[] csvRow) {
        return new ConsentConflictEvent(
                csvRow[CsvField.PATIENT_ID.columnIndex()],
                csvRow[CsvField.DATE_TIME.columnIndex()],
                csvRow[CsvField.CODE.columnIndex()],
                csvRow[CsvField.PERMIT_CONSENT_ID.columnIndex()],
                csvRow[CsvField.DENY_CONSENT_ID.columnIndex()],
                LocalDate.parse(csvRow[CsvField.OVERLAP_START.columnIndex()]),
                LocalDate.parse(csvRow[CsvField.OVERLAP_END.columnIndex()]));
    }

    public String[] toCsvElements() {
        return CsvDefinition.toCsvElements(CsvField.class, this);
    }

    public static String[] getHeaderNames() {
        return CsvDefinition.getHeaderNames(CsvField.class);
    }

    private enum CsvField implements CsvDefinition<ConsentConflictEvent> {
        PATIENT_ID("Patient-ID", ConsentConflictEvent::patientId),
        DATE_TIME("Consent-DateTime", ConsentConflictEvent::dateTime),
        CODE("Code", ConsentConflictEvent::code),
        PERMIT_CONSENT_ID("Permit-Consent-ID", ConsentConflictEvent::permitConsentId),
        DENY_CONSENT_ID("Deny-Consent-ID", ConsentConflictEvent::denyConsentId),
        OVERLAP_START("Overlap-Start", e -> e.overlapStart().toString()),
        OVERLAP_END("Overlap-End", e -> e.overlapEnd().toString());

        private final String headerName;
        private final Function<ConsentConflictEvent, String> fieldExtractor;

        CsvField(String headerName, Function<ConsentConflictEvent, String> fieldExtractor) {
            this.headerName = headerName;
            this.fieldExtractor = fieldExtractor;
        }

        @Override
        public String getHeaderName() {
            return headerName;
        }

        @Override
        public String getValue(ConsentConflictEvent event) {
            return fieldExtractor.apply(event);
        }
    }
}
