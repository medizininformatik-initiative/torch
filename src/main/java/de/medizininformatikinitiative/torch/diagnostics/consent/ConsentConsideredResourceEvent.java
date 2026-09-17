package de.medizininformatikinitiative.torch.diagnostics.consent;

import de.medizininformatikinitiative.torch.diagnostics.exclusions.CsvDefinition;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Records the consent outcome for a single resource considered during {@code DirectResourceLoader}'s
 * per-resource consent check, whether the resource was kept or excluded — torch's "Consent-Considered
 * Resources".
 *
 * @param patientId  the ID of the patient the resource belongs to
 * @param resourceId the ID of the considered resource
 * @param included   whether the resource passed the consent check
 * @param date       the date value evaluated against the consent periods, empty if not applicable
 */
public record ConsentConsideredResourceEvent(String patientId, String resourceId, boolean included, String date) {

    public ConsentConsideredResourceEvent {
        requireNonNull(patientId);
        requireNonNull(resourceId);
        requireNonNull(date);
    }

    /**
     * Converts a set of strings read directly from a CSV row to a {@link ConsentConsideredResourceEvent}.
     *
     * @param csvRow the elements of the CSV row
     * @return the newly converted {@link ConsentConsideredResourceEvent}
     */
    public static ConsentConsideredResourceEvent fromCsv(String[] csvRow) {
        return new ConsentConsideredResourceEvent(
                csvRow[CsvField.PATIENT_ID.columnIndex()],
                csvRow[CsvField.RESOURCE_ID.columnIndex()],
                Boolean.parseBoolean(csvRow[CsvField.INCLUDED.columnIndex()]),
                csvRow[CsvField.DATE.columnIndex()]);
    }

    public String[] toCsvElements() {
        return CsvDefinition.toCsvElements(CsvField.class, this);
    }

    public static String[] getHeaderNames() {
        return CsvDefinition.getHeaderNames(CsvField.class);
    }

    private enum CsvField implements CsvDefinition<ConsentConsideredResourceEvent> {
        PATIENT_ID("Patient-ID", ConsentConsideredResourceEvent::patientId),
        RESOURCE_ID("Resource-ID", ConsentConsideredResourceEvent::resourceId),
        INCLUDED("Included", e -> Boolean.toString(e.included())),
        DATE("Date", ConsentConsideredResourceEvent::date);

        private final String headerName;
        private final Function<ConsentConsideredResourceEvent, String> fieldExtractor;

        CsvField(String headerName, Function<ConsentConsideredResourceEvent, String> fieldExtractor) {
            this.headerName = headerName;
            this.fieldExtractor = fieldExtractor;
        }

        @Override
        public String getHeaderName() {
            return headerName;
        }

        @Override
        public String getValue(ConsentConsideredResourceEvent event) {
            return fieldExtractor.apply(event);
        }
    }
}
