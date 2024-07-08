package de.medizininformatikinitiative.flare.model.sq;

import de.medizininformatikinitiative.flare.Either;
import de.medizininformatikinitiative.flare.Util;
import de.medizininformatikinitiative.flare.model.Population;
import de.medizininformatikinitiative.flare.model.translate.Expression;
import de.medizininformatikinitiative.flare.model.translate.Operator;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.ParallelFlux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * A non-empty group of {@link Criterion criteria} providing operations to execute and translate them.
 *
 * @param firstCriterion the first criterion
 * @param moreCriteria   a list of more criteria
 * @param <T>            the type of the criteria that can be either {@link Criterion} itself or an additional layer of
 *                       groups
 */
public record CriterionGroup<T>(T firstCriterion, List<T> moreCriteria) {

    private static final Scheduler SCHEDULER = Schedulers.parallel();

    public CriterionGroup {
        requireNonNull(firstCriterion);
        moreCriteria = List.copyOf(moreCriteria);
    }

    public static <T> CriterionGroup<T> of(T c1) {
        return new CriterionGroup<>(c1, List.of());
    }

    public static <T> CriterionGroup<T> of(T c1, T c2) {
        return new CriterionGroup<>(c1, List.of(c2));
    }

    /**
     * Returns a group consisting of the results of applying the {@code mapper} to its {@code criteria}.
     *
     * @param mapper the function to apply on all {@code criteria} of this group
     * @param <R>    the function result type
     * @return a group consisting of the results of applying the {@code mapper} to its {@code criteria}
     */
    public <R> CriterionGroup<R> map(Function<? super T, R> mapper) {
        return new CriterionGroup<>(mapper.apply(firstCriterion), moreCriteria.stream().map(mapper).toList());
    }

    /**
     * Wraps all {@code criteria} of this group into an additional layer of groups.
     *
     * @return a criterion group of criterion groups of the original criteria
     */
    public CriterionGroup<CriterionGroup<T>> wrapCriteria() {
        return map(CriterionGroup::of);
    }

    /**
     * Executes all {@code criteria} of this group using the {@code executor} and performs a set
     * {@link Population#intersection(Population) intersection} on the results.
     */
    public Mono<Population> executeAndIntersection(Function<T, Publisher<? extends Population>> executor) {
        return parallelCriteriaFlux().flatMap(executor).reduce(Population::intersection);
    }

    /**
     * Executes all {@code criteria} of this group using the {@code executor} and performs a set
     * {@link Population#union(Population) union} on the results.
     */
    public Mono<Population> executeAndUnion(Function<T, Publisher<? extends Population>> executor) {
        return parallelCriteriaFlux().flatMap(executor).reduce(Population::union);
    }

    /**
     * Translates all {@code criteria} of this group using the {@code translator} and wraps all resulting expressions
     * into an {@link Operator#intersection(Expression) intersection} operator.
     */
    public Either<Exception, Expression> translateAndIntersection(Function<T, Either<Exception, Expression>> translator) {
        return translator.apply(firstCriterion)
                .flatMap(firstOperand -> translateMoreCriteria(translator)
                        .map(moreOperands -> Operator.intersection(firstOperand, moreOperands)));
    }

    private Either<Exception, List<Expression>> translateMoreCriteria(Function<T, Either<Exception, Expression>> translator) {
        return moreCriteria.stream().map(translator).reduce(Either.right(List.of()), Either.lift2(Util::add),
                Either.liftBinOp(Util::concat));
    }

    /**
     * Translates all {@code criteria} of this group using the {@code translator} and wraps all resulting expressions
     * into an {@link Operator#union(Expression) union} operator.
     */
    public Either<Exception, Expression> translateAndUnion(Function<T, Either<Exception, Expression>> translator) {
        return translator.apply(firstCriterion)
                .flatMap(firstOperand -> translateMoreCriteria(translator)
                        .map(moreOperands -> Operator.union(firstOperand, moreOperands)));
    }

    /**
     * Translates all {@code criteria} of this group using the {@code translator} and adds all resulting operands to
     * the first operator.
     */
    public Either<Exception, Expression> translateAndConcat(Function<T, Either<Exception, Operator<Expression>>> translator) {
        return translator.apply(firstCriterion)
                .flatMap(firstOperand -> translateMoreCriteriaFlatten(translator)
                        .map(firstOperand::addAll));
    }

    private Either<Exception, List<Expression>> translateMoreCriteriaFlatten(Function<T, Either<Exception, Operator<Expression>>> translator) {
        return moreCriteria.stream().map(translator).reduce(Either.right(List.of()), Either.lift2((list, operator) -> Util.concat(list, operator.operands())),
                Either.liftBinOp(Util::concat));
    }

    private ParallelFlux<T> parallelCriteriaFlux() {
        return Flux.fromStream(stream()).parallel().runOn(SCHEDULER);
    }

    private Stream<T> stream() {
        return Stream.concat(Stream.of(firstCriterion), moreCriteria.stream());
    }
}
