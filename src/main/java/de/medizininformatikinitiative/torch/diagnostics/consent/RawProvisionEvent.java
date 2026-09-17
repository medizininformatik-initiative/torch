package de.medizininformatikinitiative.torch.diagnostics.consent;

import de.medizininformatikinitiative.torch.diagnostics.exclusions.CsvDefinition;

import java.time.LocalDate;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Records a single consent provision as fetched from a {@code Consent} resource, before encounter-shift
 * adjustment (see {@code torch.enableEncounterShift}) — torch's "Initial Provision Periods".
 *
 * @param patientId   the ID of the patient the provision belongs to
 * @param consentId   the ID of the source {@code Consent} resource
 * @param code        the provision's consent code
 * @param permit      whether the provision is a permit ({@code true}) or a deny ({@code false})
 * @param periodStart the provision's period start
 * @param periodEnd   the provision's period end
 */
public record RawProvisionEvent(String patientId, String consentId, String code, boolean permit,
                                LocalDate periodStart, LocalDate periodEnd) {

    public RawProvisionEvent {
        requireNonNull(patientId);
        requireNonNull(consentId);
        requireNonNull(code);
        requireNonNull(periodStart);
        requireNonNull(periodEnd);
    }

    /**
     * Converts a set of strings read directly from a CSV row to a {@link RawProvisionEvent}.
     *
     * @param csvRow the elements of the CSV row
     * @return the newly converted {@link RawProvisionEvent}
     */
    public static RawProvisionEvent fromCsv(String[] csvRow) {
        return new RawProvisionEvent(
                csvRow[CsvField.PATIENT_ID.columnIndex()],
                csvRow[CsvField.CONSENT_ID.columnIndex()],
                csvRow[CsvField.CODE.columnIndex()],
                Boolean.parseBoolean(csvRow[CsvField.PERMIT.columnIndex()]),
                LocalDate.parse(csvRow[CsvField.PERIOD_START.columnIndex()]),
                LocalDate.parse(csvRow[CsvField.PERIOD_END.columnIndex()]));
    }

    public String[] toCsvElements() {
        return CsvDefinition.toCsvElements(CsvField.class, this);
    }

    public static String[] getHeaderNames() {
        return CsvDefinition.getHeaderNames(CsvField.class);
    }

    private enum CsvField implements CsvDefinition<RawProvisionEvent> {
        PATIENT_ID("Patient-ID", RawProvisionEvent::patientId),
        CONSENT_ID("Consent-ID", RawProvisionEvent::consentId),
        CODE("Code", RawProvisionEvent::code),
        PERMIT("Permit", e -> Boolean.toString(e.permit())),
        PERIOD_START("Period-Start", e -> e.periodStart().toString()),
        PERIOD_END("Period-End", e -> e.periodEnd().toString());

        private final String headerName;
        private final Function<RawProvisionEvent, String> fieldExtractor;

        CsvField(String headerName, Function<RawProvisionEvent, String> fieldExtractor) {
            this.headerName = headerName;
            this.fieldExtractor = fieldExtractor;
        }

        @Override
        public String getHeaderName() {
            return headerName;
        }

        @Override
        public String getValue(RawProvisionEvent event) {
            return fieldExtractor.apply(event);
        }
    }
}
