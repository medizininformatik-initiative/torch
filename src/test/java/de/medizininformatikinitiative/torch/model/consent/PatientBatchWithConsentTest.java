package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientBatchWithConsentFromConsentTest {

    @Test
    void includesOnlyPatientsWithNonEmptyConsent() throws ConsentViolatedException {
        PatientBatch batch = new PatientBatch(List.of("p1", "p2", "p3"));

        NonContinuousPeriod consent1 = new NonContinuousPeriod(List.of(
                Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))
        ));
        NonContinuousPeriod consent3 = new NonContinuousPeriod(List.of(
                Period.of(LocalDate.of(2024, 6, 1), LocalDate.of(2024, 12, 31))
        ));

        Map<String, NonContinuousPeriod> consentMap = Map.of(
                "p1", consent1,
                "p3", consent3
        );

        PatientBatchWithConsent result = PatientBatchWithConsent.fromBatchAndConsent(batch, consentMap);

        assertThat(result.applyConsent()).isTrue();
        assertThat(result.patientIds()).containsExactlyInAnyOrder("p1", "p3");
        assertThat(result.get("p1").consentPeriods()).isEqualTo(consent1);
        assertThat(result.get("p3").consentPeriods()).isEqualTo(consent3);
        assertThat(result.get("p2")).isNull();
    }

    @Test
    void filtersEmptyConsentPeriods() throws ConsentViolatedException {
        PatientBatch batch = new PatientBatch(List.of("p1", "p2"));

        NonContinuousPeriod nonEmpty = new NonContinuousPeriod(List.of(
                Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))
        ));
        NonContinuousPeriod empty = NonContinuousPeriod.of();

        Map<String, NonContinuousPeriod> consentMap = Map.of(
                "p1", nonEmpty,
                "p2", empty
        );

        PatientBatchWithConsent result = PatientBatchWithConsent.fromBatchAndConsent(batch, consentMap);

        assertThat(result.patientIds()).containsExactly("p1");
        assertThat(result.get("p1").consentPeriods()).isEqualTo(nonEmpty);
        assertThat(result.get("p2")).isNull();
    }

    @Test
    void throwsWhenNoPatientsHaveValidConsent() {
        PatientBatch batch = new PatientBatch(List.of("p1", "p2"));

        NonContinuousPeriod empty1 = NonContinuousPeriod.of();
        NonContinuousPeriod empty2 = NonContinuousPeriod.of();

        Map<String, NonContinuousPeriod> consentMap = Map.of(
                "p1", empty1,
                "p2", empty2
        );

        assertThatThrownBy(() -> PatientBatchWithConsent.fromBatchAndConsent(batch, consentMap))
                .isInstanceOf(ConsentViolatedException.class)
                .hasMessageContaining("No patients with valid consent periods found in batch");
    }

    @Test
    void ignoresPatientsNotInConsentMap() throws ConsentViolatedException {
        PatientBatch batch = new PatientBatch(List.of("p1", "p2", "p3"));

        NonContinuousPeriod consent1 = new NonContinuousPeriod(List.of(
                Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))
        ));

        Map<String, NonContinuousPeriod> consentMap = Map.of(
                "p1", consent1
        );

        PatientBatchWithConsent result = PatientBatchWithConsent.fromBatchAndConsent(batch, consentMap);

        assertThat(result.patientIds()).containsExactly("p1");
        assertThat(result.get("p1").consentPeriods()).isEqualTo(consent1);
        assertThat(result.get("p2")).isNull();
        assertThat(result.get("p3")).isNull();
    }
}
