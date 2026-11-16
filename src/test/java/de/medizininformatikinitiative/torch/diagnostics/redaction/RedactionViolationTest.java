package de.medizininformatikinitiative.torch.diagnostics.redaction;

import org.junit.jupiter.api.Test;

import java.util.List;

import static de.medizininformatikinitiative.torch.diagnostics.redaction.RedactionViolationReason.NO_SETTER;
import static de.medizininformatikinitiative.torch.diagnostics.redaction.RedactionViolationReason.UNMASKABLE_REQUIRED_CHILD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedactionViolationTest {

    private static final RedactionViolation VIOLATION = new RedactionViolation("Observation", "Observation.component",
            "BackboneElement", UNMASKABLE_REQUIRED_CHILD, 1, List.of("Observation/a"));

    @Test
    void missingExamplesBecomeEmpty() {
        assertThat(new RedactionViolation("Observation", "Observation.component", "BackboneElement", NO_SETTER, 1, null).examples())
                .isEmpty();
    }

    @Test
    void keyDiffersInEveryComponent() {
        assertThat(VIOLATION.sameKey(new RedactionViolation("Observation", "Observation.component", "BackboneElement", UNMASKABLE_REQUIRED_CHILD, 9, List.of())))
                .isTrue();
        assertThat(VIOLATION.sameKey(new RedactionViolation("Condition", "Observation.component", "BackboneElement", UNMASKABLE_REQUIRED_CHILD, 1, List.of())))
                .isFalse();
        assertThat(VIOLATION.sameKey(new RedactionViolation("Observation", "Observation.code", "BackboneElement", UNMASKABLE_REQUIRED_CHILD, 1, List.of())))
                .isFalse();
        assertThat(VIOLATION.sameKey(new RedactionViolation("Observation", "Observation.component", "CodeableConcept", UNMASKABLE_REQUIRED_CHILD, 1, List.of())))
                .isFalse();
        assertThat(VIOLATION.sameKey(new RedactionViolation("Observation", "Observation.component", "BackboneElement", NO_SETTER, 1, List.of())))
                .isFalse();
        assertThat(VIOLATION.sameKey(new RedactionViolation("Observation", "Observation.component", "BackboneElement", UNMASKABLE_REQUIRED_CHILD,
                new RedactionCause("Observation.component.code", "NoSuchType", NO_SETTER), 1, List.of())))
                .isFalse();
    }

    @Test
    void mergeRejectsDifferentKey() {
        RedactionViolation other = new RedactionViolation("Observation", "Observation.component", "BackboneElement", NO_SETTER, 1, List.of());

        assertThatThrownBy(() -> VIOLATION.merge(other)).isInstanceOf(IllegalArgumentException.class);
    }
}
