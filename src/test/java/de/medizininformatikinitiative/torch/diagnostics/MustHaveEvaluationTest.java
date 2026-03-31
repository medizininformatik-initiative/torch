package de.medizininformatikinitiative.torch.diagnostics;

import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MustHaveEvaluationTest {

    private static final AnnotatedAttribute ATTRIBUTE =
            new AnnotatedAttribute("attr-ref", "Observation.status", true);

    @Nested
    class NotApplicableVariant {

        @Test
        void instance_isNotNull() {
            assertThat(MustHaveEvaluation.NotApplicable.INSTANCE).isNotNull();
        }

        @Test
        void instance_implementsMustHaveEvaluation() {
            assertThat(MustHaveEvaluation.NotApplicable.INSTANCE)
                    .isInstanceOf(MustHaveEvaluation.class);
        }

        @Test
        void twoInstances_areEqual() {
            var a = new MustHaveEvaluation.NotApplicable();
            var b = new MustHaveEvaluation.NotApplicable();

            assertThat(a).isEqualTo(b);
        }
    }

    @Nested
    class FulfilledVariant {

        @Test
        void instance_isNotNull() {
            assertThat(MustHaveEvaluation.Fulfilled.INSTANCE).isNotNull();
        }

        @Test
        void instance_implementsMustHaveEvaluation() {
            assertThat(MustHaveEvaluation.Fulfilled.INSTANCE)
                    .isInstanceOf(MustHaveEvaluation.class);
        }

        @Test
        void twoInstances_areEqual() {
            var a = new MustHaveEvaluation.Fulfilled();
            var b = new MustHaveEvaluation.Fulfilled();

            assertThat(a).isEqualTo(b);
        }
    }

    @Nested
    class ViolatedVariant {

        @Test
        void storesFirstViolated() {
            var violated = new MustHaveEvaluation.Violated(ATTRIBUTE);

            assertThat(violated.firstViolated()).isSameAs(ATTRIBUTE);
        }

        @Test
        void throwsWhenFirstViolatedIsNull() {
            assertThatThrownBy(() -> new MustHaveEvaluation.Violated(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessage("firstViolated");
        }

        @Test
        void twoInstancesWithSameAttribute_areEqual() {
            var a = new MustHaveEvaluation.Violated(ATTRIBUTE);
            var b = new MustHaveEvaluation.Violated(ATTRIBUTE);

            assertThat(a).isEqualTo(b);
        }

        @Test
        void twoInstancesWithDifferentAttributes_areNotEqual() {
            var other = new AnnotatedAttribute("other-ref", "Observation.code", false);
            var a = new MustHaveEvaluation.Violated(ATTRIBUTE);
            var b = new MustHaveEvaluation.Violated(other);

            assertThat(a).isNotEqualTo(b);
        }
    }
}
