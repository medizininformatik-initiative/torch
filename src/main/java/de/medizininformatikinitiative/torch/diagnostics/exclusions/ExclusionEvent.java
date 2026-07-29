package de.medizininformatikinitiative.torch.diagnostics.exclusions;

import java.util.Arrays;

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

    /**
     * Helper for converting an exclusion event to a CSV row.
     *
     * @param fieldClass    defines the CSV structure of the ExclusionEvent {@link T}
     * @param event         the ExclusionEvent to be converted
     * @return              the row of column elements of the ExclusionEvent as an ordered array of strings, each being
     *                      a column element
     * @param <T>           the type of {@link ExclusionEvent}
     * @param <E>           the CSV definition of the type {@link T} of ExclusionEvent
     */
    static <T extends ExclusionEvent, E extends Enum<E> & CsvDefinition<T>> String[] toCsvElements(Class<E> fieldClass, T event) {
        return Arrays.stream(fieldClass.getEnumConstants())
                .map(field -> field.getValue(event))
                .toArray(String[]::new);
    }

    /**
     * Returns a human-readable CSV header row for a type of {@link ExclusionEvent}.
     * <p>
     * Allows for the CSV structure of the exclusion event, defined by {@link E}, to be declared independently and as
     * private in each subclass of {@link ExclusionEvent}, while avoiding duplicated code by calling this method
     * in each implementation of {@link #toCsvElements()}.
     *
     * @param fieldClass    defines the CSV structure of the ExclusionEvent {@link T}
     * @return              the  header row of column elements of the ExclusionEvent as an ordered array of strings,
     *                      each being a column element
     * @param <T>           the type of {@link ExclusionEvent}
     * @param <E>           the CSV definition of the type {@link T} of ExclusionEvent
     */
    static <T extends ExclusionEvent, E extends Enum<E> & CsvDefinition<T>> String[] getHeaderNames(Class<E> fieldClass) {
        return Arrays.stream(fieldClass.getEnumConstants())
                .map(CsvDefinition::getHeaderName)
                .toArray(String[]::new);
    }

}
