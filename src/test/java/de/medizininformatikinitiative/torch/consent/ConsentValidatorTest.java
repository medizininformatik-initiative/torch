package de.medizininformatikinitiative.torch.consent;


import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.consent.Provisions;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ConsentValidatorTest {

    private static final String PATIENT_ID = "123";
    private static final Map<String, Provisions> STATIC_PROVISIONS_MAP = createStaticProvisions();
    private PatientResourceBundle patientResourceBundle;
    Observation observation;

    @Autowired
    private ConsentValidator consentValidator;

    @BeforeEach
    void setUp() {
        patientResourceBundle = new PatientResourceBundle(PATIENT_ID, STATIC_PROVISIONS_MAP.get(PATIENT_ID));
        observation = new Observation();
        observation.setId("123");
    }

    @Test
    void checkConsent_shouldReturnTrue_whenResourceWithinAnyConsentPeriod() {
        observation.setEffective(new DateTimeType("2022-04-20"));
        boolean result = consentValidator.checkConsent(observation, patientResourceBundle);
        assertThat(result).isTrue();

    }

    @Test
    void checkConsent_shouldReturnFalse_whenResourceOutsideAllConsentPeriods() {
        observation.setEffective(new DateTimeType("2018-04-20"));
        boolean result = consentValidator.checkConsent(observation, patientResourceBundle);
        assertThat(result).isFalse();

    }

    @Test
    void checkConsent_shouldReturnTrue_whenResourceWithinSecondConsentPeriod() {
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

    private static Map<String, Provisions> createStaticProvisions() {
        Map<String, Provisions> provisionsMap = new HashMap<>();

        Period consentPeriod1 = Period.of(LocalDate.of(2021, 1, 1), LocalDate.of(2025, 12, 31));
        Period consentPeriod2 = Period.of(LocalDate.of(2028, 1, 1), LocalDate.of(2030, 12, 31));

        List<Period> multiplePeriods = Arrays.asList(consentPeriod1, consentPeriod2);

        Provisions provisions = new Provisions(
                Map.of("consent-key", new NonContinuousPeriod(multiplePeriods))
        );
        provisionsMap.put(PATIENT_ID, provisions);
        return provisionsMap;
    }
}