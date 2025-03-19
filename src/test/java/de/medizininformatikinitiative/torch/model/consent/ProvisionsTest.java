package de.medizininformatikinitiative.torch.model.consent;

import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConsentProvisionsStaticTest {

    private static final String PATIENT_ID = "123";
    private static final Map<String, Provisions> STATIC_PROVISIONS_MAP = createStaticProvisions();
    private Provisions patientProvisions;

    @BeforeEach
    void setUp() {
        patientProvisions = STATIC_PROVISIONS_MAP.get(PATIENT_ID);
        assertNotNull(patientProvisions, "Provisions should not be null");
    }

    @Test
    void checkConsent_shouldAllowObservationWithinAnyConsentPeriod() {
        Observation observation = new Observation();
        observation.setId("obs-1");
        observation.setEffective(new DateTimeType("2023-06-15"));

        Period resourcePeriod = Period.fromHapi(observation.getEffectiveDateTimeType());
        assertTrue(patientProvisions.periods().get("consent-key").within(resourcePeriod), "Consent should be valid");
    }

    @Test
    void checkConsent_shouldDenyObservationOutsideAllConsentPeriods() {
        Observation observation = new Observation();
        observation.setId("obs-2");
        observation.setEffective(new DateTimeType("2027-01-01"));

        Period resourcePeriod = Period.fromHapi(observation.getEffectiveDateTimeType());
        assertFalse(patientProvisions.periods().get("consent-key").within(resourcePeriod), "Consent should be invalid");
    }

    @Test
    void checkConsent_shouldAllowObservationWithinSecondConsentPeriod() {
        Observation observation = new Observation();
        observation.setId("obs-3");
        observation.setEffective(new DateTimeType("2029-06-15"));

        Period resourcePeriod = Period.fromHapi(observation.getEffectiveDateTimeType());
        assertTrue(patientProvisions.periods().get("consent-key").within(resourcePeriod), "Consent should be valid within second period");
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

