package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.consent.ProvisionExtractor;
import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.consent.ConsentProvisions;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.consent.Provision;
import de.medizininformatikinitiative.torch.model.management.TermCode;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Reference;
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

    static final String MII_CONSENT_SYSTEM = "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3";
    static final TermCode MDAT_WISSENSCHAFTLICH_NUTZEN = new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.8");
    static final TermCode MDAT_RETROSPEKTIV_WISSENSCHAFTLICH_NUTZEN = new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.46");
    static final TermCode KRANKENKASSENDATEN = new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.10");
    static final TermCode REKONTAKTIERUNG_ERGEBNISSE = new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.37");
    static final TermCode REKONTAKTIERUNG_ERGAENZUNGEN = new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.26");
    static final TermCode REKONTAKTIERUNG_VERKNUEPFUNG_DATENBANKEN = new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.27");
    static final TermCode REKONTAKTIERUNG_WEITERE_ERHEBUNG = new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.28");
    static final TermCode REKONTAKTIERUNG_WEITERE_STUDIEN = new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.29");
    static final TermCode REKONTAKTIERUNG_ZUSATZBEFUND_31 = new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.31");
    static final TermCode REKONTAKTIERUNG_ZUSATZBEFUND_30 = new TermCode(MII_CONSENT_SYSTEM, "2.16.840.1.113883.3.1937.777.24.5.3.30");
    static final Set<TermCode> MII_CONSENT_CODES = Set.of(
            MDAT_WISSENSCHAFTLICH_NUTZEN, MDAT_RETROSPEKTIV_WISSENSCHAFTLICH_NUTZEN, KRANKENKASSENDATEN,
            REKONTAKTIERUNG_ERGEBNISSE, REKONTAKTIERUNG_ERGAENZUNGEN, REKONTAKTIERUNG_VERKNUEPFUNG_DATENBANKEN,
            REKONTAKTIERUNG_WEITERE_ERHEBUNG, REKONTAKTIERUNG_WEITERE_STUDIEN,
            REKONTAKTIERUNG_ZUSATZBEFUND_31, REKONTAKTIERUNG_ZUSATZBEFUND_30
    );

    protected static final Logger logger = LoggerFactory.getLogger(ProvisionExtractorIT.class);

    private final IntegrationTestSetup integrationTestSetup = new IntegrationTestSetup();

    ProvisionExtractorIT() throws IOException {
    }

    @Test
    void irrelevantCodesSkipped() throws ConsentViolatedException, PatientIdNotFoundException {
        ProvisionExtractor processor = new ProvisionExtractor();
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

        ConsentProvisions consentProvisions = processor.extractProvisionsPeriodByCode(consent, MII_CONSENT_CODES);

        assertThat(consentProvisions.provisions().isEmpty());
    }


    @Test
    void deniesExtracted() throws ConsentViolatedException, PatientIdNotFoundException {
        ProvisionExtractor processor = new ProvisionExtractor();
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

        ConsentProvisions consentProvisions = processor.extractProvisionsPeriodByCode(consent, MII_CONSENT_CODES);

        Provision expected = new Provision(
                new TermCode(
                        "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
                        "2.16.840.1.113883.3.1937.777.24.5.3.10"
                ),
                de.medizininformatikinitiative.torch.model.consent.Period.of("2021-01-01", "2025-12-31"),
                false // DENY => permit = false
        );

        assertThat(consentProvisions.provisions()).isEqualTo(List.of(expected));
    }


    @ParameterizedTest
    @ValueSource(strings = {"VHF006_Consent.json"})
    void extractsPermits(String resource) throws IOException, ConsentViolatedException, PatientIdNotFoundException {
        ProvisionExtractor processor = new ProvisionExtractor();
        Set<TermCode> consentCodes = MII_CONSENT_CODES;
        Consent consent = (Consent) integrationTestSetup.readResource("src/test/resources/InputResources/Consent/" + resource);

        ConsentProvisions consentProvisions = processor.extractProvisionsPeriodByCode(consent, consentCodes);
        List<Provision> provisions = consentProvisions.provisions();

        assertThat(provisions.isEmpty()).isFalse();

        Set<TermCode> actualCodes = provisions.stream()
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

        Consent consent = new Consent();
        consent.setId("consent-no-date");
        consent.setPatient(new Reference("Patient/123"));
        consent.setStatus(Consent.ConsentState.ACTIVE);
        consent.setDateTimeElement(null); // triggers the branch

        assertThatThrownBy(() -> processor.extractProvisionsPeriodByCode(consent, MII_CONSENT_CODES))
                .isInstanceOf(ConsentViolatedException.class)
                .hasMessageContaining("consent-no-date has no valid consent date");
    }

    @Test
    void throwsConsentViolatedException_whenConsentDateTimeIsEmpty() {
        ProvisionExtractor processor = new ProvisionExtractor();

        Consent consent = new Consent();
        consent.setId("consent-empty-date");
        consent.setPatient(new Reference("Patient/123"));
        consent.setStatus(Consent.ConsentState.ACTIVE);
        consent.setDateTimeElement(new DateTimeType()); // empty date triggers the branch

        assertThatThrownBy(() -> processor.extractProvisionsPeriodByCode(consent, MII_CONSENT_CODES))
                .isInstanceOf(ConsentViolatedException.class)
                .hasMessageContaining("consent-empty-date has no valid consent date");
    }

    @Test
    void skipsProvisionsWithoutStartOrEnd() throws ConsentViolatedException, PatientIdNotFoundException {
        ProvisionExtractor processor = new ProvisionExtractor();

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

        ConsentProvisions consentProvisions = processor.extractProvisionsPeriodByCode(consent, MII_CONSENT_CODES);

        // Should be empty because the provision is skipped
        assertThat(consentProvisions.provisions().isEmpty());
    }
}
