package de.medizininformatikinitiative.flare.model.fhir;

import de.medizininformatikinitiative.flare.model.sq.Comparator;
import de.medizininformatikinitiative.flare.model.sq.Quantity;
import de.medizininformatikinitiative.flare.model.sq.TermCode;

import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * An immutable list of query params.
 * <p>
 * In order to build a list of query params, start either with {@link #EMPTY} or the {@link #of(String, Value) creator
 * method} and continue with the {@link #appendParam(String, Value) appendParam} method.
 */
public record QueryParams(List<Param> params) {

    public static QueryParams EMPTY = new QueryParams(List.of());

    public QueryParams {
        params = List.copyOf(params);
    }

    public static QueryParams of(String name, Value value) {
        return EMPTY.appendParam(requireNonNull(name), requireNonNull(value));
    }

    public static Value stringValue(String value) {
        return new StringValue(requireNonNull(value));
    }

    public static Value conceptValue(TermCode value) {
        return new ConceptValue(requireNonNull(value));
    }

    public static Value dateValue(Comparator comparator, LocalDate value) {
        return new DateValue(requireNonNull(comparator), requireNonNull(value));
    }

    public static Value quantityValue(Comparator comparator, Quantity value) {
        return new QuantityValue(requireNonNull(comparator), requireNonNull(value));
    }

    public static Value compositeConceptValue(TermCode compositeCode, TermCode value) {
        return new CompositeConceptValue(requireNonNull(compositeCode), requireNonNull(value));
    }

    public static Value compositeQuantityValue(TermCode compositeCode, Comparator comparator, Quantity value) {
        return new CompositeQuantityValue(requireNonNull(compositeCode), requireNonNull(comparator),
                requireNonNull(value));
    }

    /**
     * Appends a param with {@code name} and {@code value}.
     *
     * @param name  the name of the query parameter
     * @param value the value of the query parameter
     * @return the {@code QueryParams} resulting in appending the param
     */
    public QueryParams appendParam(String name, Value value) {
        var sb = new LinkedList<>(this.params);
        sb.add(new Param(requireNonNull(name), requireNonNull(value)));
        return new QueryParams(sb);
    }

    /**
     * Appends a params by calling the function {@code appendTo}.
     *
     * @param params a function that takes a {@code QueryParams}, appends some params returning the resulting {@code QueryParams}
     * @return the {@code QueryParams} resulting in appending the params
     */
    public QueryParams appendParams(QueryParams params) {
        var sb = new LinkedList<>(this.params);
        sb.addAll(params.params);
        return new QueryParams(sb);
    }

    /**
     * Prefixes the name of each {@code Param query param} with {@code name} followed by an {@literal .}.
     *
     * @param name the name to use a prefix
     * @return the {@code QueryParams} resulting in prefixing
     */
    public QueryParams prefixName(String name) {
        return new QueryParams(params.stream().map(param -> new Param(name + "." + param.name, param.value)).toList());
    }

    @Override
    public String toString() {
        return params.stream().map(Param::toString).collect(Collectors.joining("&"));
    }

    private record Param(String name, Value value) {

        @Override
        public String toString() {
            return name + "=" + value;
        }
    }

    /**
     * A value of a query param.
     */
    interface Value {
    }

    private record StringValue(String value) implements Value {

        private StringValue {
            requireNonNull(value);
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private record ConceptValue(TermCode value) implements Value {

        private ConceptValue {
            requireNonNull(value);
        }

        @Override
        public String toString() {
            return value.searchValue();
        }
    }

    private record DateValue(Comparator comparator, LocalDate value) implements Value {

        private DateValue {
            requireNonNull(comparator);
            requireNonNull(value);
        }

        @Override
        public String toString() {
            return comparator.toString() + value;
        }
    }

    private record QuantityValue(Comparator comparator, Quantity value) implements Value {

        private QuantityValue {
            requireNonNull(comparator);
            requireNonNull(value);
        }

        @Override
        public String toString() {
            return comparator.toString() + value.searchValue();
        }
    }

    private record CompositeConceptValue(TermCode compositeCode, TermCode value) implements Value {

        private CompositeConceptValue {
            requireNonNull(compositeCode);
            requireNonNull(value);
        }

        @Override
        public String toString() {
            return compositeCode.searchValue() + "$" + value.searchValue();
        }
    }

    private record CompositeQuantityValue(TermCode compositeCode, Comparator comparator, Quantity value)
            implements Value {

        private CompositeQuantityValue {
            requireNonNull(compositeCode);
            requireNonNull(comparator);
            requireNonNull(value);
        }

        @Override
        public String toString() {
            return compositeCode.searchValue() + "$" + comparator + value.searchValue();
        }
    }
}
