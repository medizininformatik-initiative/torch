package de.medizininformatikinitiative.torch.diagnostics;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;

import static java.util.Objects.requireNonNull;

/**
 * Result of evaluating a must-have constraint against a resource.
 */
public sealed interface MustHaveEvaluation
        permits MustHaveEvaluation.NotApplicable,
        MustHaveEvaluation.Fulfilled,
        MustHaveEvaluation.Violated {

    /**
     * The constraint was not relevant to the evaluated resource.
     */
    record NotApplicable() implements MustHaveEvaluation {
        static final NotApplicable INSTANCE = new NotApplicable();
    }

    /**
     * The constraint was applicable and fully satisfied.
     */
    record Fulfilled() implements MustHaveEvaluation {
        static final Fulfilled INSTANCE = new Fulfilled();
    }

    /**
     * The constraint was applicable but violated.
     *
     * @param firstViolated the first {@link AnnotatedAttribute} that caused the violation
     */
    record Violated(AnnotatedAttribute firstViolated) implements MustHaveEvaluation {
        public Violated {
            requireNonNull(firstViolated, "firstViolated");
        }
    }
}
