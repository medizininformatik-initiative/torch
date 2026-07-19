package de.medizininformatikinitiative.torch.diagnostics.exclusions;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Records a moment when a patient is excluded from further processing.
 *
 * @param stage     the stage at which the exclusion is happening
 * @param patientId the ID of the excluded patient
 */
public record PatientExclusionEvent(PatientExclusionStage stage, String patientId) implements ExclusionEvent {

    public PatientExclusionEvent {
        requireNonNull(stage);
        requireNonNull(patientId);
    }

    /**
     * Converts a set of strings read directly from a CSV row to a {@link PatientExclusionEvent}.
     *
     * @param csvRow    the elements of the CSV row
     * @return          the newly converted {@link PatientExclusionEvent}
     */
    public static PatientExclusionEvent fromCsv(String[] csvRow) {
        return new PatientExclusionEvent(
                PatientExclusionStage.valueOf(csvRow[CsvField.STAGE.columnIndex()]),
                csvRow[CsvField.PATIENT_ID.columnIndex()]);
    }

    @Override
    public String[] toCsvElements() {
        return ExclusionEvent.toCsvElements(CsvField.class, this);
    }

    public static String[] getHeaderNames() {
        return ExclusionEvent.getHeaderNames(CsvField.class);
    }

    /**
     * Defines the structure of the {@link PatientExclusionEvent} as ordered fields in a CSV row.
     */
    private enum CsvField implements CsvDefinition<PatientExclusionEvent> {
        STAGE("Stage", e -> e.stage.toString()),
        PATIENT_ID("Patient-ID", PatientExclusionEvent::patientId);

        public final String headerName;
        private final Function<PatientExclusionEvent, String> fieldExtractor;

        CsvField(String headerName, Function<PatientExclusionEvent, String> fieldExtractor) {
            this.headerName = headerName;
            this.fieldExtractor = fieldExtractor;
        }

        @Override
        public String getHeaderName() {
            return headerName;
        }

        @Override
        public String getValue(PatientExclusionEvent event) {
            return fieldExtractor.apply(event);
        }
    }
}
