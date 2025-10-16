package de.medizininformatikinitiative.torch.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.consent.ConsentCodeMapper;
import de.medizininformatikinitiative.torch.consent.ProvisionExtractor;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.consent.ConsentCode;
import de.medizininformatikinitiative.torch.model.consent.ConsentProvisions;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.consent.Provision;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ProvisionExtractorIT {

    protected static final Logger logger = LoggerFactory.getLogger(ProvisionExtractorIT.class);

    private final IntegrationTestSetup integrationTestSetup = new IntegrationTestSetup();
    private final ConsentCodeMapper consentCodeMapper;

    public ProvisionExtractorIT() throws IOException {
        // Initialize the ConsentCodeMapper as before
        consentCodeMapper = new ConsentCodeMapper("src/test/resources/mappings/consent-mappings.json", new ObjectMapper());
    }


    @Test
    void irrelevantCodesSkipped() throws ConsentViolatedException, PatientIdNotFoundException {
        ProvisionExtractor processor = new ProvisionExtractor();
        Set<ConsentCode> expectedCodes = consentCodeMapper.getCombinedCodes(new ConsentCode("fdpg.mii.cds", "yes-yes-yes-yes"));
        Consent consent = new Consent();
        consent.setPatient(new Reference("Patient/123"));
        consent.setDateTime(new DateTimeType("2023-01-01").getValue());
        consent.setStatus(Consent.ConsentState.ACTIVE);
        Consent.ProvisionComponent provision = new Consent.ProvisionComponent();
        provision.setType(Consent.ConsentProvisionType.PERMIT);
        provision.setCode(List.of(new CodeableConcept(new Coding("InvalidSystem", "InvalidCode", "Invalid Code"))));
        provision.setPeriod(new org.hl7.fhir.r4.model.Period().setStartElement(new org.hl7.fhir.r4.model.DateTimeType("2021-01-01"))
                .setEndElement(new org.hl7.fhir.r4.model.DateTimeType("2025-12-31")));
        consent.setProvision(provision);

        ConsentProvisions consentProvisions = processor.extractProvisionsPeriodByCode(consent, expectedCodes);

        assertThat(consentProvisions.provisions().isEmpty());
    }


    @Test
    void deniesExtracted() throws ConsentViolatedException, PatientIdNotFoundException {
        ProvisionExtractor processor = new ProvisionExtractor();
        Set<ConsentCode> expectedCodes = consentCodeMapper.getCombinedCodes(new ConsentCode("fdpg.mii.cds", "yes-yes-yes-yes"));
        Consent consent = new Consent();
        consent.setPatient(new Reference("Patient/123"));
        consent.setDateTime(new DateTimeType("2023-01-01").getValue());
        consent.setStatus(Consent.ConsentState.ACTIVE);
        Consent.ProvisionComponent provision = new Consent.ProvisionComponent();
        provision.setType(Consent.ConsentProvisionType.PERMIT);
        Consent.ProvisionComponent nestedProvision = new Consent.ProvisionComponent();
        nestedProvision.setType(Consent.ConsentProvisionType.DENY);
        nestedProvision.setCode(List.of(new CodeableConcept(new Coding("urn:oid:2.16.840.1.113883.3.1937.777.24.5.3", "2.16.840.1.113883.3.1937.777.24.5.3.10", ""))));
        nestedProvision.setPeriod(new org.hl7.fhir.r4.model.Period().setStartElement(new org.hl7.fhir.r4.model.DateTimeType("2021-01-01"))
                .setEndElement(new org.hl7.fhir.r4.model.DateTimeType("2025-12-31")));
        nestedProvision.setType(Consent.ConsentProvisionType.DENY);

        provision.setProvision(List.of(nestedProvision));
        consent.setProvision(provision);

        ConsentProvisions consentProvisions = processor.extractProvisionsPeriodByCode(consent, expectedCodes);

        assertThat(consentProvisions.provisions()).isEqualTo(List.of(Provision.fromHapi(nestedProvision)));
    }


    @ParameterizedTest
    @ValueSource(strings = {"VHF006_Consent.json"})
    void extractsPermits(String resource) throws IOException, ConsentViolatedException, PatientIdNotFoundException {
        ProvisionExtractor processor = new ProvisionExtractor();
        Set<ConsentCode> consentCodes = consentCodeMapper.getCombinedCodes(new ConsentCode("fdpg.mii.cds", "yes-yes-yes-yes"));
        Consent consent = (Consent) integrationTestSetup.readResource("src/test/resources/InputResources/Consent/" + resource);

        ConsentProvisions consentProvisions = processor.extractProvisionsPeriodByCode(consent, consentCodes);
        List<Provision> provisions = consentProvisions.provisions();

        assertThat(provisions.isEmpty()).isFalse();

        Set<ConsentCode> actualCodes = provisions.stream()
                .map(Provision::code)
                .collect(Collectors.toSet());
        assertThat(actualCodes).isEqualTo(consentCodes);
        for (Provision provision : provisions) {
            logger.info("Provision Code: {}", provision.code());
            logger.info("Provision Periods: {}", provision.period());
            assertThat(provision.period()).isEqualTo((Period.of(LocalDate.of(2021, 1, 1), LocalDate.of(2025, 12, 31))));
            assertThat(provision.permit()).isTrue();
        }
    }

    @Test
    void throwsConsentViolatedException_whenConsentHasNoDateTime() {
        ProvisionExtractor processor = new ProvisionExtractor();
        Set<ConsentCode> expectedCodes = consentCodeMapper.getCombinedCodes(new ConsentCode("fdpg.mii.cds", "yes-yes-yes-yes"));

        Consent consent = new Consent();
        consent.setId("consent-no-date");
        consent.setPatient(new Reference("Patient/123"));
        consent.setStatus(Consent.ConsentState.ACTIVE);
        consent.setDateTimeElement(null); // triggers the branch

        assertThatThrownBy(() -> processor.extractProvisionsPeriodByCode(consent, expectedCodes))
                .isInstanceOf(ConsentViolatedException.class)
                .hasMessageContaining("consent-no-date has no valid consent date");
    }

    @Test
    void throwsConsentViolatedException_whenConsentDateTimeIsEmpty() {
        ProvisionExtractor processor = new ProvisionExtractor();
        Set<ConsentCode> expectedCodes = consentCodeMapper.getCombinedCodes(new ConsentCode("fdpg.mii.cds", "yes-yes-yes-yes"));

        Consent consent = new Consent();
        consent.setId("consent-empty-date");
        consent.setPatient(new Reference("Patient/123"));
        consent.setStatus(Consent.ConsentState.ACTIVE);
        consent.setDateTimeElement(new DateTimeType()); // empty date triggers the branch

        assertThatThrownBy(() -> processor.extractProvisionsPeriodByCode(consent, expectedCodes))
                .isInstanceOf(ConsentViolatedException.class)
                .hasMessageContaining("consent-empty-date has no valid consent date");
    }

    @Test
    void skipsProvisionsWithoutStartOrEnd() throws ConsentViolatedException, PatientIdNotFoundException {
        ProvisionExtractor processor = new ProvisionExtractor();
        Set<ConsentCode> expectedCodes = consentCodeMapper.getCombinedCodes(new ConsentCode("fdpg.mii.cds", "yes-yes-yes-yes"));

        Consent consent = new Consent();
        consent.setPatient(new Reference("Patient/123"));
        consent.setDateTime(new DateTimeType("2023-01-01").getValue());
        consent.setStatus(Consent.ConsentState.ACTIVE);

        Consent.ProvisionComponent provision = new Consent.ProvisionComponent();
        provision.setType(Consent.ConsentProvisionType.PERMIT);
        provision.setCode(List.of(new CodeableConcept(new Coding("urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
                "2.16.840.1.113883.3.1937.777.24.5.3.10", ""))));
        // No start and end set → should be skipped
        consent.setProvision(provision);

        ConsentProvisions consentProvisions = processor.extractProvisionsPeriodByCode(consent, expectedCodes);

        // Should be empty because the provision is skipped
        assertThat(consentProvisions.provisions().isEmpty());
    }
}
