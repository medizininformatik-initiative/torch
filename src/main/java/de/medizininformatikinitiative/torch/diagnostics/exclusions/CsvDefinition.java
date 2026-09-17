package de.medizininformatikinitiative.torch.diagnostics.exclusions;

import java.util.Arrays;

/**
 * Helper for defining the CSV structure of a row type {@link T} written by {@link de.medizininformatikinitiative.torch.diagnostics.DiagnosticsStore}.
 * Is intended to be implemented only by {@link Enum} classes.
 *
 * @param <T> the type of row this CSV structure describes
 */
public interface CsvDefinition<T> {

    String getHeaderName();
    String getValue(T element);

    default int columnIndex() {
        return ((Enum<?> )this).ordinal() + 1; // +1 for Batch-ID
    }

    /**
     * Converts a row to a CSV row.
     *
     * @param fieldClass    defines the CSV structure of the row type {@link T}
     * @param element       the row to be converted
     * @return              the row of column elements as an ordered array of strings, each being a column element
     * @param <T>           the type of row
     * @param <E>           the CSV definition of the type {@link T}
     */
    static <T, E extends Enum<E> & CsvDefinition<T>> String[] toCsvElements(Class<E> fieldClass, T element) {
        return Arrays.stream(fieldClass.getEnumConstants())
                .map(field -> field.getValue(element))
                .toArray(String[]::new);
    }

    /**
     * Returns a human-readable CSV header row for a row type {@link T}.
     * <p>
     * Allows for the CSV structure of the row, defined by {@link E}, to be declared independently and as
     * private in each row type, while avoiding duplicated code.
     *
     * @param fieldClass    defines the CSV structure of the row type {@link T}
     * @return              the header row of column elements as an ordered array of strings, each being a column element
     * @param <T>           the type of row
     * @param <E>           the CSV definition of the type {@link T}
     */
    static <T, E extends Enum<E> & CsvDefinition<T>> String[] getHeaderNames(Class<E> fieldClass) {
        return Arrays.stream(fieldClass.getEnumConstants())
                .map(CsvDefinition::getHeaderName)
                .toArray(String[]::new);
    }

}


