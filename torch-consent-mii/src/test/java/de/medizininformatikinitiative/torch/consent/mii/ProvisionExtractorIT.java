package de.medizininformatikinitiative.torch.consent.mii;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.consent.ConsentViolatedException;
import de.medizininformatikinitiative.torch.consent.mii.model.ConsentProvisions;
import de.medizininformatikinitiative.torch.consent.mii.model.Provision;
import de.medizininformatikinitiative.torch.consent.mii.model.TermCode;
import de.medizininformatikinitiative.torch.model.consent.Period;
import org.hl7.fhir.r4.model.Consent;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * Exercises {@link ProvisionExtractor} against a real MII Consent resource fixture, complementing
 * {@link ProvisionExtractorTest}'s hand-built cases.
 */
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

    private static final Logger logger = LoggerFactory.getLogger(ProvisionExtractorIT.class);
    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();

    @Test
    void extractsPermits() throws IOException, ConsentViolatedException {
        ProvisionExtractor processor = new ProvisionExtractor();
        String json = Files.readString(Path.of("src/test/resources/InputResources/Consent/VHF006_Consent.json"));
        Consent consent = (Consent) FHIR_CONTEXT.newJsonParser().parseResource(json);

        ConsentProvisions consentProvisions = processor.extractProvisionsPeriodByCode("VHF00006", consent, MII_CONSENT_CODES);
        List<Provision> provisions = consentProvisions.provisions();

        assertThat(provisions.isEmpty()).isFalse();

        Set<TermCode> actualCodes = provisions.stream()
                .map(Provision::code)
                .collect(Collectors.toSet());
        assertThat(actualCodes).isEqualTo(MII_CONSENT_CODES);
        for (Provision provision : provisions) {
            logger.info("Provision Code: {}", provision.code());
            logger.info("Provision Periods: {}", provision.period());
            assertThat(provision.period()).isEqualTo(Period.of(LocalDate.of(2021, 1, 1), LocalDate.of(2025, 12, 31)));
            assertThat(provision.permit()).isTrue();
        }
    }
}
