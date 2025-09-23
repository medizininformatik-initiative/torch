package de.medizininformatikinitiative.torch.consent;


import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.exceptions.ReferenceToPatientException;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConsentValidatorTest {

    private static final String PATIENT_ID = "123";
    private PatientResourceBundle patientResourceBundle;


    @Autowired
    private ConsentValidator consentValidator;

    private static NonContinuousPeriod noncontinousPeriod() {
        Period consentPeriod1 = Period.of(LocalDate.of(2021, 1, 1), LocalDate.of(2025, 12, 31));
        Period consentPeriod2 = Period.of(LocalDate.of(2028, 1, 1), LocalDate.of(2030, 12, 31));

        List<Period> multiplePeriods = Arrays.asList(consentPeriod1, consentPeriod2);
        return new NonContinuousPeriod(multiplePeriods);
    }

    @Test
    void checkConsent_shouldReturnTrue_whenResourceWithinAnyConsentPeriod() {
        Observation observation = new Observation();
        observation.setEffective(new DateTimeType("2022-04-20"));
        boolean result = consentValidator.checkConsent(observation, patientResourceBundle);
        assertThat(result).isTrue();

    }

    @Test
    void checkConsent_shouldReturnFalse_whenResourceOutsideAllConsentPeriods() {
        Observation observation = new Observation();
        observation.setEffective(new DateTimeType("2018-04-20"));
        boolean result = consentValidator.checkConsent(observation, patientResourceBundle);
        assertThat(result).isFalse();

    }

    @Test
    void checkConsent_shouldReturnTrue_whenResourceWithinSecondConsentPeriod() {
        Observation observation = new Observation();
        observation.setEffective(new DateTimeType("2029-04-20"));
        boolean result = consentValidator.checkConsent(observation, patientResourceBundle);
        assertThat(result).isTrue();
    }

    @Test
    void notTimeDependent() {
        Medication medication = new Medication();
        boolean result = consentValidator.checkConsent(medication, patientResourceBundle);
        assertThat(result).isTrue();

    }

    @BeforeEach
    void setUp() {
        patientResourceBundle = new PatientResourceBundle(PATIENT_ID, noncontinousPeriod());

    }

    @Test
    void checkConsent_shouldReturnFalse_whenNoPatientBundleFound() {
        Observation observation = new Observation();
        // Resource has patient reference
        observation.setEffective(new DateTimeType("2022-04-20"));
        observation.setSubject(new Reference("Patient/999")); // patientId ≠ 123

        // PatientBatchWithConsent with NO entry for "999"
        PatientBatchWithConsent batch = new PatientBatchWithConsent(
                Map.of(PATIENT_ID, patientResourceBundle), true
        );

        boolean result = consentValidator.checkConsent(observation, batch);

        assertThat(result).isFalse();
    }

    @Test
    void checkConsent_shouldReturnFalse_whenPatientIdNotFound() {
        // Create a resource with NO patient reference
        Observation noPatientObs = new Observation();
        noPatientObs.setEffective(new DateTimeType("2022-04-20"));
        // subject is missing → ResourceUtils.patientId() will throw PatientIdNotFoundException

        PatientBatchWithConsent batch = new PatientBatchWithConsent(
                Map.of(PATIENT_ID, patientResourceBundle), true
        );

        boolean result = consentValidator.checkConsent(noPatientObs, batch);

        assertThat(result).isFalse();
    }

    @Test
    void checkConsent_shouldReturnFalse_whenNoPatientResourceBundleFound() {
        Observation observation = new Observation();
        // Resource points to a different patient
        observation.setSubject(new Reference("Patient/999"));
        observation.setEffective(new DateTimeType("2022-04-20"));

        // Batch contains only "123", not "999"
        PatientBatchWithConsent batch = new PatientBatchWithConsent(
                Map.of(PATIENT_ID, patientResourceBundle), true
        );

        boolean result = consentValidator.checkConsent(observation, batch);

        assertThat(result).isFalse();
    }


    @Nested
    class CheckConsent {

        @Test
        void missMatchPatientIDs() {
            Condition condition = new Condition();
            condition.setSubject(new Reference("Patient/1"));

            assertThatThrownBy(() -> consentValidator.checkPatientIdAndConsent(patientResourceBundle, true, condition))
                    .isInstanceOf(ReferenceToPatientException.class)
                    .hasMessageContaining("Patient loaded reference belonging to another patient");

        }

        @Test
        void consentViolated() {
            Condition condition = new Condition();
            condition.setSubject(new Reference("Patient/123"));

            assertThatThrownBy(() -> consentValidator.checkPatientIdAndConsent(patientResourceBundle, true, condition))
                    .isInstanceOf(ConsentViolatedException.class)
                    .hasMessageContaining("Consent Violated in Patient Resource");

        }

        @Test
        void noValidPatientReference() {
            Condition condition = new Condition();
            condition.setSubject(new Reference("Unknown/123"));

            assertThatThrownBy(() -> consentValidator.checkPatientIdAndConsent(patientResourceBundle, true, condition))
                    .isInstanceOf(PatientIdNotFoundException.class)
                    .hasMessageContaining("Reference does not start with 'Patient/': Unknown/123");

        }


        @Test
        void successWithoutConsent() throws PatientIdNotFoundException, ReferenceToPatientException, ConsentViolatedException {
            Condition condition = new Condition();
            condition.setSubject(new Reference("Patient/123"));

            assertThat(consentValidator.checkPatientIdAndConsent(patientResourceBundle, false, condition)).isTrue();

        }

        @Test
        void successWithConsent() throws PatientIdNotFoundException, ReferenceToPatientException, ConsentViolatedException {
            Observation observation = new Observation();
            observation.setEffective(new DateTimeType("2029-04-20"));
            observation.setSubject(new Reference("Patient/123"));
            assertThat(consentValidator.checkPatientIdAndConsent(patientResourceBundle, true, observation)).isTrue();
        }
    }
}
