package de.medizininformatikinitiative.torch.diagnostics.exclusions;



/**
 * Helper for defining the CSV structure of a {@link ExclusionEvent}. Is intended to be implemented only by {@link Enum}
 * classes.
 *
 * @param <T> the type of {@link ExclusionEvent}
 */
public interface CsvDefinition<T extends ExclusionEvent> {

    String getHeaderName();
    String getValue(T element);

    default int columnIndex() {
        return ((Enum<?> )this).ordinal() + 1; // +1 for Batch-ID
    }

}


