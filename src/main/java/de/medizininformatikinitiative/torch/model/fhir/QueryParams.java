package de.medizininformatikinitiative.torch.model.fhir;

import de.medizininformatikinitiative.torch.model.crtdl.Code;
import de.medizininformatikinitiative.torch.model.sq.Comparator;

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

    public static Value dateValue(Comparator comparator, LocalDate value) {
        return new DateValue(requireNonNull(comparator), requireNonNull(value));
    }

    public static Value codeValue(Code value) {
        return new CodeValue(value);
    }

    ;

    private record CodeValue(Code value) implements Value {

        private CodeValue {
            requireNonNull(value);
        }

        @Override
        public String toString() {
            return value.toString();
        }
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

    public record Param(String name, Value value) {

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


}
