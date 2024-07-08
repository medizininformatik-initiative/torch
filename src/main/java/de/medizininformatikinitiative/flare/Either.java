package de.medizininformatikinitiative.flare;

import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * A union type of either a {@link Either.Left left} or a {@link Either.Right right} value.
 *
 * <p>Usually Right denotes the happy path and Left denotes the exception case. Operations like {@link #map(Function)
 * map} and {@link #flatMap(Function) flatMap} are right-based. So they operate on the right value and shortcut in the
 * case the Either is {@link Either.Left left}.
 *
 * @param <L> the type of the left value
 * @param <R> the type of the right value
 */
public sealed interface Either<L, R> permits Either.Left, Either.Right {

    /**
     * Wraps the given value into a {@link Either.Left Left Either}.
     *
     * @param val the value to wrap
     * @param <L> the type of the left value
     * @param <R> the type of the right value
     * @return a Left Either
     */
    static <L, R> Either<L, R> left(L val) {
        return new Left<>(val);
    }

    /**
     * Wraps the given value into a {@link Either.Right Right Either}.
     *
     * @param val the value to wrap
     * @param <L> the type of the left value
     * @param <R> the type of the right value
     * @return a Right Either
     */
    static <L, R> Either<L, R> right(R val) {
        return new Right<>(val);
    }

    /**
     * Lifts the function {@code f} into the {@code Either} type.
     *
     * <p>The lifted function will return it's argument if it is {@link Either.Left left} without calling {@code f},
     * also called shortcutting. In case the argument is {@link Either.Right right}, the function will call {@code f}
     * with the {@link Either.Right#val right value} and return the result wrapped into a {@link Either.Right
     * Right Either}.
     *
     * <p>Applying the lifted function to an {@code Either} is the same as calling {@link #map(Function) map} with
     * {@code f}.
     *
     * @param f   the function to lift
     * @param <L> the left type of all Eithers
     * @param <T> the argument type of {@code f}
     * @param <R> the result type of {@code f}
     * @return a function that takes an {@code Either<L, T>} instead of a {@code T} and returns an {@code Either<L, R>}
     * instead of a {@code R}
     */
    static <L, T, R> Function<Either<L, T>, Either<L, R>> lift(Function<T, R> f) {
        requireNonNull(f, "f");
        return either -> either.map(f);
    }

    /**
     * Lifts the function {@code f} into the {@code Either} type.
     *
     * <p>The lifted function will return one of it's arguments if it is {@link Either.Left left} without calling
     * {@code f}, also called shortcutting. In case both arguments are {@link Either.Right right}, the function will
     * call {@code f} and return the result wrapped into a {@link Either.Right Right Either}.
     *
     * <p>Lifting an existing function is useful in
     * {@link java.util.stream.Stream#reduce(Object, BiFunction, BinaryOperator) reducing} streams of eithers.
     *
     * @param f   the function to lift
     * @param <L> the left type of all Eithers
     * @param <T> the type of the first argument of {@code f}
     * @param <U> the type of the second argument of {@code f}
     * @param <R> the result type of {@code f}
     * @return a function that takes an {@code Either<L, T>} and an {@code Either<L, U>} instead of a {@code T} and an
     * {@code U} and returns an {@code Either<L, R>} instead of a {@code R}
     */
    static <L, T, U, R> BiFunction<Either<L, T>, Either<L, U>, Either<L, R>> lift2(BiFunction<T, U, R> f) {
        requireNonNull(f, "f");
        return (eitherA, eitherB) -> eitherA.flatMap(a -> eitherB.map(b -> f.apply(a, b)));
    }

    /**
     * Lifts the binary operator {@code op} into the {@code Either} type.
     *
     * <p>The lifted operator will return one of it's arguments if it is {@link Either.Left left} without calling
     * {@code op}, also called shortcutting. In case both arguments are {@link Either.Right right}, the operator will
     * call {@code op} and return the result wrapped into a {@link Either.Right Right Either}.
     *
     * <p>Lifting an existing operators is useful in {@link java.util.stream.Stream#reduce(BinaryOperator) reducing}
     * streams of Eithers.
     *
     * @param op  the operator to lift
     * @param <L> the left type of all Eithers
     * @param <T> the type of {@code op}
     * @return an operator that takes two {@code Either<L, T>}s of two {@code T}s and returns an {@code Either<L, T>}
     * instead of a {@code T}
     */
    static <L, T> BinaryOperator<Either<L, T>> liftBinOp(BinaryOperator<T> op) {
        requireNonNull(op, "op");
        return (eitherA, eitherB) -> eitherA.flatMap(a -> eitherB.map(b -> op.apply(a, b)));
    }

    /**
     * If this {@code Either} is {@link Either.Right right}, returns the result of applying the given mapping function
     * to the {@link Either.Right#val right value} wrapped into a {@link Either.Right Right Either}, otherwise returns
     * the left {@code Either} unchanged.
     *
     * @param mapper the mapping function to apply to a right value
     * @param <U>    the type of the mapped right value
     * @return a mapped right value wrapped in a {@code Right Either} or an unchanged left {@code Either}
     * @throws NullPointerException if the mapping function is {@code null}
     */
    <U> Either<L, U> map(Function<? super R, ? extends U> mapper);

    /**
     * If this {@code Either} is {@link Either.Right right}, returns the result of applying the given mapping function
     * to the {@link Either.Right#val right value}, otherwise returns the left {@code Either} unchanged.
     *
     * @param mapper the mapping function to apply to a right value
     * @param <U>    the type of the mapped right value
     * @return a mapped right value or an unchanged left {@code Either}
     * @throws NullPointerException if the mapping function is {@code null}
     */
    <U> Either<L, U> flatMap(Function<? super R, ? extends Either<L, U>> mapper);

    /**
     * Maps both, the right and the left value to a value of a common type {@code T}.
     *
     * @param leftMapper  the mapping function that is applied to the left value
     * @param rightMapper the mapping function that is applied to the right value
     * @param <T>         the type of the return value
     * @return the return value of either either applying the left value to the {@code leftMapper} or the right value to
     * the {@code rightMapper}
     * @throws NullPointerException if one of the mapping functions is {@code null}
     */
    <T> T either(Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends T> rightMapper);

    /**
     * A container representing the left value.
     *
     * @param <L> the type of the left value
     * @param <R> the type of the right value
     */
    record Left<L, R>(L val) implements Either<L, R> {

        @Override
        @SuppressWarnings("unchecked")
        public <U> Either<L, U> map(Function<? super R, ? extends U> mapper) {
            return (Either<L, U>) this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <U> Either<L, U> flatMap(Function<? super R, ? extends Either<L, U>> mapper) {
            return (Either<L, U>) this;
        }

        @Override
        public <T> T either(Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends T> rightMapper) {
            requireNonNull(leftMapper, "left mapper");
            return leftMapper.apply(val);
        }
    }

    /**
     * A container representing the left value.
     *
     * @param val the right value
     * @param <L> the type of the left value
     * @param <R> the type of the right value
     */
    record Right<L, R>(R val) implements Either<L, R> {

        @Override
        public <U> Either<L, U> map(Function<? super R, ? extends U> mapper) {
            requireNonNull(mapper);
            return new Right<>(mapper.apply(val));
        }

        @Override
        public <U> Either<L, U> flatMap(Function<? super R, ? extends Either<L, U>> mapper) {
            requireNonNull(mapper, "mapper");
            return mapper.apply(val);
        }

        @Override
        public <T> T either(Function<? super L, ? extends T> leftMapper, Function<? super R, ? extends T> rightMapper) {
            requireNonNull(rightMapper, "right mapper");
            return rightMapper.apply(val);
        }
    }
}
