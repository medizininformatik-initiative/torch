package de.medizininformatikinitiative.torch.diagnostics.exclusions;



import jakarta.validation.constraints.NotBlank;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;


/**
 * Records a moment when a resource is excluded from further processing.
 * <p>
 * The information a resource exclusion event holds varies depending on the {@link ResourceExclusionReason} (e.g. for must-have
 * exclusions there exists a specific attribute that is violated, while consent violations happen without looking at attributes).
 * For simplicity, the different variations of events are all represented by this class, filling the fields depending
 * on which information is available at that event.
 * This leads to the {@code patientId} and {@code attributeRef} being allowed to be empty.
 *
 * @param reason       the reason for which the resource was excluded
 * @param groupId      the ID of the AttributeGroup the resource belongs to
 * @param resourceId   the ID of the excluded resource.
 * @param patientId    the ID of the excluded patient (allowed to be empty if not available)
 * @param attributeRef the attribute reference of the excluded resource (allowed to be empty if not available)
 * @param detail       further detail on the reason (allowed to be empty if not available); for
 *                     {@link ResourceExclusionReason#CONSENT} this is the {@code ConsentCheckOutcome} name
 */
public record ResourceExclusionEvent(ResourceExclusionReason reason,
                                     @NotBlank String groupId,
                                     @NotBlank String resourceId,
                                     String patientId,
                                     String attributeRef,
                                     String detail) implements ExclusionEvent {

    public ResourceExclusionEvent {
        requireNonNull(patientId);
        requireNonNull(reason);
        requireNonNull(groupId);
        requireNonNull(resourceId);
        requireNonNull(attributeRef);
        requireNonNull(detail);
    }

    /**
     * Converts a set of strings read directly from a CSV row to a {@link ResourceExclusionEvent}.
     * <p>
     * Tolerates a row written before the {@code Detail} column existed (a batch directory from before this
     * column was added, read back across a TORCH upgrade while the job was still in flight) by defaulting
     * {@code detail} to {@code ""} rather than failing.
     *
     * @param csvRow    the elements of the CSV row
     * @return          the newly converted {@link ResourceExclusionEvent}
     */
    public static ResourceExclusionEvent fromCsv(String[] csvRow) {
        int detailIndex = CsvField.DETAIL.columnIndex();
        return new ResourceExclusionEvent(
                ResourceExclusionReason.valueOf(csvRow[CsvField.REASON.columnIndex()]), csvRow[CsvField.GROUP.columnIndex()], csvRow[CsvField.RESOURCE_ID.columnIndex()], csvRow[CsvField.PATIENT_ID.columnIndex()],
                csvRow[CsvField.ATTRIBUTE.columnIndex()], csvRow.length > detailIndex ? csvRow[detailIndex] : "");
    }

    @Override
    public String[] toCsvElements() {
        return CsvDefinition.toCsvElements(CsvField.class, this);
    }

    public static String[] getHeaderNames() {
        return CsvDefinition.getHeaderNames(CsvField.class);
    }

    /**
     * Defines the structure of the {@link ResourceExclusionEvent} as ordered fields in a CSV row.
     */
    private enum CsvField implements CsvDefinition<ResourceExclusionEvent> {
        REASON ("Reason", e -> e.reason().toString()),
        GROUP ("Group", ResourceExclusionEvent::groupId),
        ATTRIBUTE ("Attribute", ResourceExclusionEvent::attributeRef),
        RESOURCE_ID ("Resource-ID", ResourceExclusionEvent::resourceId),
        PATIENT_ID ("Patient-ID", ResourceExclusionEvent::patientId),
        DETAIL ("Detail", ResourceExclusionEvent::detail);

        public final String headerName;
        private final Function<ResourceExclusionEvent, String> fieldExtractor;

        CsvField(String headerName, Function<ResourceExclusionEvent, String> fieldExtractor) {
            this.headerName = headerName;
            this.fieldExtractor = fieldExtractor;
        }

        @Override
        public String getHeaderName() {
            return headerName;
        }

        @Override
        public String getValue(ResourceExclusionEvent event) {
            return fieldExtractor.apply(event);
        }
    }
}

