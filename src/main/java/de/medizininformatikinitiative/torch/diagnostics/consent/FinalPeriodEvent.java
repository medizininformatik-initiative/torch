package de.medizininformatikinitiative.torch.diagnostics.consent;

import de.medizininformatikinitiative.torch.diagnostics.exclusions.CsvDefinition;

import java.time.LocalDate;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Records one disjoint segment of a patient's final, intersected data-extraction consent period, as
 * computed by {@code ConsentCalculator.calculateConsent}.
 *
 * @param patientId   the ID of the patient
 * @param periodStart the segment's start
 * @param periodEnd   the segment's end
 */
public record FinalPeriodEvent(String patientId, LocalDate periodStart, LocalDate periodEnd) {

    public FinalPeriodEvent {
        requireNonNull(patientId);
        requireNonNull(periodStart);
        requireNonNull(periodEnd);
    }

    /**
     * Converts a set of strings read directly from a CSV row to a {@link FinalPeriodEvent}.
     *
     * @param csvRow the elements of the CSV row
     * @return the newly converted {@link FinalPeriodEvent}
     */
    public static FinalPeriodEvent fromCsv(String[] csvRow) {
        return new FinalPeriodEvent(
                csvRow[CsvField.PATIENT_ID.columnIndex()],
                LocalDate.parse(csvRow[CsvField.PERIOD_START.columnIndex()]),
                LocalDate.parse(csvRow[CsvField.PERIOD_END.columnIndex()]));
    }

    public String[] toCsvElements() {
        return CsvDefinition.toCsvElements(CsvField.class, this);
    }

    public static String[] getHeaderNames() {
        return CsvDefinition.getHeaderNames(CsvField.class);
    }

    private enum CsvField implements CsvDefinition<FinalPeriodEvent> {
        PATIENT_ID("Patient-ID", FinalPeriodEvent::patientId),
        PERIOD_START("Period-Start", e -> e.periodStart().toString()),
        PERIOD_END("Period-End", e -> e.periodEnd().toString());

        private final String headerName;
        private final Function<FinalPeriodEvent, String> fieldExtractor;

        CsvField(String headerName, Function<FinalPeriodEvent, String> fieldExtractor) {
            this.headerName = headerName;
            this.fieldExtractor = fieldExtractor;
        }

        @Override
        public String getHeaderName() {
            return headerName;
        }

        @Override
        public String getValue(FinalPeriodEvent event) {
            return fieldExtractor.apply(event);
        }
    }
}
