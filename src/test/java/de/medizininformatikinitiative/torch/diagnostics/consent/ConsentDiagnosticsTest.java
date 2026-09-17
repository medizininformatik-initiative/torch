package de.medizininformatikinitiative.torch.diagnostics.consent;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentDiagnosticsTest {

    @Test
    void equals_isReflexive() {
        ConsentDiagnostics diagnostics = ConsentDiagnostics.create(true);
        diagnostics.addRawProvision(new RawProvisionEvent("p1", "c1", "code", true,
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31)));

        assertThat(diagnostics).isEqualTo(diagnostics);
    }

    @Test
    void equals_returnsFalse_forDifferentType() {
        ConsentDiagnostics diagnostics = ConsentDiagnostics.create(true);

        assertThat(diagnostics).isNotEqualTo("not a ConsentDiagnostics");
    }

    @Test
    void hashCode_isConsistent_forEqualInstances() {
        ConsentDiagnostics first = ConsentDiagnostics.create(true);
        ConsentDiagnostics second = ConsentDiagnostics.create(true);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }
}
