package de.medizininformatikinitiative.flare.model.translate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Operator expression that consists of a {@code name} and a list of {@code operands}.
 * <p>
 * There are currently the different kind of operators defined in the {@link Name} enum. If new kinds will be added,
 * there should be also new constructor methods.
 *
 * @param name     the name of the operator
 * @param operands the operands which are itself expressions
 */
@JsonIgnoreProperties({"empty"})
public record Operator<T extends Expression>(Name name, List<? extends T> operands) implements Expression {

    public Operator {
        requireNonNull(name);
        if (operands.isEmpty()) {
            throw new IllegalArgumentException("empty operands");
        }
        operands = List.copyOf(operands);
    }

    /**
     * Creates a new {@code difference} operator.
     *
     * @param o1 the first operand
     * @param o2 the second operand
     * @return the new difference operator
     */
    public static Operator<Expression> difference(Expression o1, Expression o2) {
        return new Operator<>(Name.DIFFERENCE, List.of(o1, o2));
    }

    /**
     * Creates a new {@code intersection} operator.
     *
     * @param o1 the first operand
     * @return the new intersection operator
     */
    public static <T extends Expression> Operator<T> intersection(T o1) {
        return new Operator<>(Name.INTERSECTION, List.of(o1));
    }

    /**
     * Creates a new {@code intersection} operator.
     *
     * @param o1 the first operand
     * @param o2 the second operand
     * @return the new intersection operator
     */
    public static <T extends Expression> Operator<T> intersection(T o1, T o2) {
        return new Operator<>(Name.INTERSECTION, List.of(o1, o2));
    }

    /**
     * Creates a new {@code intersection} operator.
     *
     * @param firstOperand the first operand
     * @param moreOperands more operands
     * @return the new intersection operator
     */
    public static <T extends Expression> Operator<T> intersection(T firstOperand, List<? extends T> moreOperands) {
        return new Operator<>(Name.INTERSECTION, Stream.concat(Stream.of(firstOperand), moreOperands.stream()).toList());
    }

    /**
     * Creates a new {@code union} operator.
     *
     * @param o1 the first operand
     * @return the new union operator
     */
    public static <T extends Expression> Operator<T> union(T o1) {
        return new Operator<>(Name.UNION, List.of(o1));
    }

    /**
     * Creates a new {@code union} operator.
     *
     * @param o1 the first operand
     * @param o2 the second operand
     * @return the new union operator
     */
    public static <T extends Expression> Operator<T> union(T o1, T o2) {
        return new Operator<>(Name.UNION, List.of(o1, o2));
    }

    /**
     * Creates a new {@code union} operator.
     *
     * @param firstOperand the first operand
     * @param moreOperands more operands
     * @return the new union operator
     */
    public static <T extends Expression> Operator<T> union(T firstOperand, List<? extends T> moreOperands) {
        return new Operator<>(Name.UNION, Stream.concat(Stream.of(firstOperand), moreOperands.stream()).toList());
    }

    public Operator<T> add(T operand) {
        return new Operator<>(name, Stream.concat(this.operands.stream(), Stream.of(operand)).toList());
    }

    public Operator<T> addAll(List<? extends T> operands) {
        return new Operator<>(name, Stream.concat(this.operands.stream(), operands.stream()).toList());
    }

    public enum Name {
        @JsonProperty("difference") DIFFERENCE,
        @JsonProperty("intersection") INTERSECTION,
        @JsonProperty("union") UNION
    }
}
