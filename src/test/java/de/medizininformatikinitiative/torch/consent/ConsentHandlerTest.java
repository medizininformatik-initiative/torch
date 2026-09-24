package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.diagnostics.consent.FinalPeriodEvent;
import de.medizininformatikinitiative.torch.diagnostics.consent.RawProvisionEvent;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.consent.ConsentCodeConfig;
import de.medizininformatikinitiative.torch.model.consent.ConsentProvisions;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.consent.Provision;
import de.medizininformatikinitiative.torch.model.management.PatientBatch;
import de.medizininformatikinitiative.torch.model.management.TermCode;
import org.hl7.fhir.r4.model.DateTimeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ConsentHandlerTest {

    public static final String PATIENT_ID = "VHF00006";
    public static final String UNKNOWN_PATIENT_ID = "Unknown";
    public static final PatientBatch BATCH = PatientBatch.of(PATIENT_ID);
    public static final PatientBatch BATCH_UNKNOWN = PatientBatch.of(UNKNOWN_PATIENT_ID);
    public static final Set<TermCode> CODES = Set.of(new TermCode("sys", "code1"));

    @Mock
    ConsentFetcher consentFetcher;
    @Mock
    ConsentAdjuster consentAdjuster;
    @Mock
    ConsentCalculator consentCalculator;

    @Mock
    ConsentCalculator consentCalculationFailed;

    @Mock
    ConsentCodeConfig consentCodeConfig;

    ConsentHandler consentHandler;

    @BeforeEach
    void setUp() {
        consentHandler = new ConsentHandler(consentFetcher, consentAdjuster, consentCalculator, consentCodeConfig, true);
    }

    @Test
    void failsOnNoPatientMatchesConsentKeyBuildingConsent() {
        var codes = CODES;
        when(consentCodeConfig.extractRequestedProspectiveCodes(codes)).thenReturn(codes);
        when(consentCodeConfig.withRetroModifiers(codes, codes)).thenReturn(codes);
        when(consentFetcher.fetchConsentInfo(codes, BATCH))
                .thenReturn(Mono.error(new ConsentViolatedException("No valid consentPeriods found for any patients in batch")));

        StepVerifier.create(consentHandler.fetchAndBuildConsentInfo(codes, BATCH))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid consentPeriods found for any patients in batch"))
                .verify();
    }

    @Test
    void failsOnUnknownPatientBuildingConsent() {

        var codes = CODES;
        when(consentCodeConfig.extractRequestedProspectiveCodes(codes)).thenReturn(codes);
        when(consentCodeConfig.withRetroModifiers(codes, codes)).thenReturn(codes);

        when(consentFetcher.fetchConsentInfo(codes, BATCH_UNKNOWN))
                .thenReturn(Mono.error(new ConsentViolatedException("No valid consentPeriods found for any patients in batch")));

        StepVerifier.create(consentHandler.fetchAndBuildConsentInfo(codes, BATCH_UNKNOWN))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ConsentViolatedException.class)
                        .hasMessageContaining("No valid consentPeriods found for any patients in batch"))
                .verify();
    }

    private Map<String, List<ConsentProvisions>> provisionsByPatient() {
        return Map.of(PATIENT_ID, List.of(new ConsentProvisions("c1", PATIENT_ID, new DateTimeType("2024-01-01T00:00:00Z"), List.of())));
    }

    private Map<String, NonContinuousPeriod> consentPeriodsByPatient() {
        return Map.of(PATIENT_ID, NonContinuousPeriod.of(new Period(LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1))));
    }

    @Test
    void encounterShiftEnabledInvokesConsentAdjuster() {
        var codes = CODES;
        var fetchedProvisions = provisionsByPatient();
        var adjustedProvisions = provisionsByPatient();

        when(consentCodeConfig.extractRequestedProspectiveCodes(codes)).thenReturn(codes);
        when(consentCodeConfig.withRetroModifiers(codes, codes)).thenReturn(codes);
        when(consentCodeConfig.nonGateCodes(codes)).thenReturn(codes);
        when(consentFetcher.fetchConsentInfo(codes, BATCH)).thenReturn(Mono.just(fetchedProvisions));
        when(consentAdjuster.fetchEncounterAndAdjustByEncounter(BATCH, fetchedProvisions, codes)).thenReturn(Mono.just(adjustedProvisions));
        when(consentCalculator.calculateConsent(codes, adjustedProvisions)).thenReturn(consentPeriodsByPatient());

        StepVerifier.create(consentHandler.fetchAndBuildConsentInfo(codes, BATCH))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly(PATIENT_ID))
                .verifyComplete();

        verify(consentAdjuster).fetchEncounterAndAdjustByEncounter(BATCH, fetchedProvisions, codes);
    }

    @Test
    void encounterShiftDisabledSkipsConsentAdjuster() {
        var codes = CODES;
        var fetchedProvisions = provisionsByPatient();
        var handler = new ConsentHandler(consentFetcher, consentAdjuster, consentCalculator, consentCodeConfig, false);

        when(consentCodeConfig.extractRequestedProspectiveCodes(codes)).thenReturn(codes);
        when(consentCodeConfig.withRetroModifiers(codes, codes)).thenReturn(codes);
        when(consentCodeConfig.nonGateCodes(codes)).thenReturn(codes);
        when(consentFetcher.fetchConsentInfo(codes, BATCH)).thenReturn(Mono.just(fetchedProvisions));
        when(consentCalculator.calculateConsent(codes, fetchedProvisions)).thenReturn(consentPeriodsByPatient());

        StepVerifier.create(handler.fetchAndBuildConsentInfo(codes, BATCH))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly(PATIENT_ID))
                .verifyComplete();

        verifyNoInteractions(consentAdjuster);
    }

    @Test
    void recordsRawProvisionsAndFinalPeriodsWhenDiagnosticsEnabled() {
        var codes = CODES;
        var batch = PatientBatch.of(PATIENT_ID).withConsentDiagnosticsEnabled(true);
        var provision = new Provision(new TermCode("sys", "code1"), new Period(LocalDate.of(2021, 1, 1), LocalDate.of(2025, 12, 31)), true);
        var consentProvisions = new ConsentProvisions("c1", PATIENT_ID, new DateTimeType("2021-01-01T00:00:00Z"), List.of(provision));
        var fetchedProvisions = Map.of(PATIENT_ID, List.of(consentProvisions));
        var handler = new ConsentHandler(consentFetcher, consentAdjuster, consentCalculator, consentCodeConfig, false);

        when(consentCodeConfig.extractRequestedProspectiveCodes(codes)).thenReturn(codes);
        when(consentCodeConfig.withRetroModifiers(codes, codes)).thenReturn(codes);
        when(consentCodeConfig.nonGateCodes(codes)).thenReturn(codes);
        when(consentFetcher.fetchConsentInfo(codes, batch)).thenReturn(Mono.just(fetchedProvisions));
        when(consentCalculator.calculateConsent(codes, fetchedProvisions)).thenReturn(consentPeriodsByPatient());

        StepVerifier.create(handler.fetchAndBuildConsentInfo(codes, batch))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly(PATIENT_ID))
                .verifyComplete();

        var consentDiagnostics = batch.diagnostics().consentDiagnostics();
        assertThat(consentDiagnostics.getRawProvisions()).containsExactly(
                new RawProvisionEvent(PATIENT_ID, "c1", "code1", true,
                        LocalDate.of(2021, 1, 1), LocalDate.of(2025, 12, 31)));
        assertThat(consentDiagnostics.getFinalPeriods()).containsExactly(
                new FinalPeriodEvent(PATIENT_ID, LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1)));
    }

    @Test
    void recordsRawProvisionsBeforeEncounterShiftApplies() {
        var codes = CODES;
        var batch = PatientBatch.of(PATIENT_ID).withConsentDiagnosticsEnabled(true);
        var rawProvision = new Provision(new TermCode("sys", "code1"), new Period(LocalDate.of(2021, 1, 1), LocalDate.of(2025, 12, 31)), true);
        var shiftedProvision = new Provision(new TermCode("sys", "code1"), new Period(LocalDate.of(2020, 6, 1), LocalDate.of(2025, 12, 31)), true);
        var rawConsentProvisions = new ConsentProvisions("c1", PATIENT_ID, new DateTimeType("2021-01-01T00:00:00Z"), List.of(rawProvision));
        var shiftedConsentProvisions = new ConsentProvisions("c1", PATIENT_ID, new DateTimeType("2021-01-01T00:00:00Z"), List.of(shiftedProvision));
        var fetchedProvisions = Map.of(PATIENT_ID, List.of(rawConsentProvisions));
        var adjustedProvisions = Map.of(PATIENT_ID, List.of(shiftedConsentProvisions));

        when(consentCodeConfig.extractRequestedProspectiveCodes(codes)).thenReturn(codes);
        when(consentCodeConfig.withRetroModifiers(codes, codes)).thenReturn(codes);
        when(consentCodeConfig.nonGateCodes(codes)).thenReturn(codes);
        when(consentFetcher.fetchConsentInfo(codes, batch)).thenReturn(Mono.just(fetchedProvisions));
        when(consentAdjuster.fetchEncounterAndAdjustByEncounter(batch, fetchedProvisions, codes)).thenReturn(Mono.just(adjustedProvisions));
        when(consentCalculator.calculateConsent(codes, adjustedProvisions)).thenReturn(consentPeriodsByPatient());

        StepVerifier.create(consentHandler.fetchAndBuildConsentInfo(codes, batch))
                .assertNext(result -> assertThat(result.patientIds()).containsExactly(PATIENT_ID))
                .verifyComplete();

        // recorded from the pre-shift fetch result, not the encounter-shifted one fed into ConsentCalculator
        assertThat(batch.diagnostics().consentDiagnostics().getRawProvisions()).containsExactly(
                new RawProvisionEvent(PATIENT_ID, "c1", "code1", true,
                        LocalDate.of(2021, 1, 1), LocalDate.of(2025, 12, 31)));
    }

}
