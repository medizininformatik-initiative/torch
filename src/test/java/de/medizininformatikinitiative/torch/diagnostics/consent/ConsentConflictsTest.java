package de.medizininformatikinitiative.torch.diagnostics.consent;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentConflictsTest {

    private static ConsentConflictEvent conflict(String patientId) {
        return new ConsentConflictEvent(patientId, "2025-01-01", "code", "c1", "c1",
                LocalDate.of(2025, 1, 1), LocalDate.of(2050, 12, 31));
    }

    @Test
    void equals_isReflexive() {
        ConsentConflicts conflicts = ConsentConflicts.empty();
        conflicts.add(conflict("p1"));

        assertThat(conflicts).isEqualTo(conflicts);
    }

    @Test
    void equals_returnsFalse_forDifferentType() {
        assertThat(ConsentConflicts.empty()).isNotEqualTo("not a ConsentConflicts");
    }

    @Test
    void equals_returnsFalse_forDifferentEvents() {
        ConsentConflicts first = ConsentConflicts.empty();
        first.add(conflict("p1"));
        ConsentConflicts second = ConsentConflicts.empty();
        second.add(conflict("p2"));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void hashCode_isConsistent_forEqualInstances() {
        ConsentConflicts first = ConsentConflicts.empty();
        first.add(conflict("p1"));
        ConsentConflicts second = ConsentConflicts.empty();
        second.add(conflict("p1"));

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
