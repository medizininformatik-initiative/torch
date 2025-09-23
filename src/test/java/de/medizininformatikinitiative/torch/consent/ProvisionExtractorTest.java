package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.exceptions.PatientIdNotFoundException;
import de.medizininformatikinitiative.torch.model.consent.ConsentProvisions;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.consent.Provision;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProvisionExtractorTest {

    private ProvisionExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ProvisionExtractor();
    }

    @Test
    void extractsNestedPermitWithMissingAndPresentPeriods() throws Exception {
        Consent consent = new Consent();
        consent.setId("consent-nested");
        consent.setPatient(new Reference("Patient/123"));
        consent.setDateTimeElement(new DateTimeType("2023-01-01"));

        // Nested permit with both start and end → should be included
        Consent.ProvisionComponent nestedValid = new Consent.ProvisionComponent();
        nestedValid.setType(Consent.ConsentProvisionType.PERMIT);
        nestedValid.setCode(List.of(new CodeableConcept(new Coding("sys", "code1", "desc"))));
        nestedValid.setPeriod(new org.hl7.fhir.r4.model.Period()
                .setStartElement(new DateTimeType("2022-01-01"))
                .setEndElement(new DateTimeType("2022-12-31")));

        // Nested permit missing start → should be skipped
        Consent.ProvisionComponent nestedMissingStart = new Consent.ProvisionComponent();
        nestedMissingStart.setType(Consent.ConsentProvisionType.PERMIT);
        nestedMissingStart.setCode(List.of(new CodeableConcept(new Coding("sys", "code2", "desc"))));
        nestedMissingStart.setPeriod(new org.hl7.fhir.r4.model.Period()
                .setEndElement(new DateTimeType("2022-12-31")));

        // Nested permit missing end → should be skipped
        Consent.ProvisionComponent nestedMissingEnd = new Consent.ProvisionComponent();
        nestedMissingEnd.setType(Consent.ConsentProvisionType.PERMIT);
        nestedMissingEnd.setCode(List.of(new CodeableConcept(new Coding("sys", "code3", "desc"))));
        nestedMissingEnd.setPeriod(new org.hl7.fhir.r4.model.Period()
                .setStartElement(new DateTimeType("2022-01-01")));

        // Parent provision containing all nested provisions
        Consent.ProvisionComponent parentProvision = new Consent.ProvisionComponent();
        parentProvision.setProvision(List.of(nestedValid, nestedMissingStart, nestedMissingEnd));
        consent.setProvision(parentProvision);

        ConsentProvisions result = extractor.extractProvisionsPeriodByCode(consent, Set.of("code1", "code2", "code3"));

        // Only the valid nested provision should be included
        assertThat(result.provisions()).hasSize(1);
        Provision p = result.provisions().getFirst();
        assertThat(p.code()).isEqualTo("code1");
        assertThat(p.period()).isEqualTo(Period.of(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31)));
        assertThat(p.permit()).isTrue();
    }

    @Test
    void skipsIrrelevantCodes() throws ConsentViolatedException, PatientIdNotFoundException {
        Consent consent = new Consent();
        consent.setId("consent2");
        consent.setPatient(new Reference("Patient/123"));
        consent.setDateTimeElement(new DateTimeType("2023-01-01"));

        Consent.ProvisionComponent nestedProvision = new Consent.ProvisionComponent();
        nestedProvision.setType(Consent.ConsentProvisionType.PERMIT);
        nestedProvision.setCode(List.of(new CodeableConcept(new Coding("sys", "codeX", "desc"))));
        nestedProvision.setPeriod(new org.hl7.fhir.r4.model.Period()
                .setStartElement(new DateTimeType("2022-01-01"))
                .setEndElement(new DateTimeType("2022-12-31")));

        Consent.ProvisionComponent parentProvision = new Consent.ProvisionComponent();
        parentProvision.setProvision(List.of(nestedProvision));
        consent.setProvision(parentProvision);

        ConsentProvisions result = extractor.extractProvisionsPeriodByCode(consent, Set.of("code1"));

        assertThat(result.provisions()).isEmpty();
    }

    @Test
    void extractsNestedDeny() throws ConsentViolatedException, PatientIdNotFoundException {
        Consent consent = new Consent();
        consent.setId("consent3");
        consent.setPatient(new Reference("Patient/123"));
        consent.setDateTimeElement(new DateTimeType("2023-01-01"));

        Consent.ProvisionComponent nestedDeny = new Consent.ProvisionComponent();
        nestedDeny.setType(Consent.ConsentProvisionType.DENY);
        nestedDeny.setCode(List.of(new CodeableConcept(new Coding("sys", "code1", ""))));
        nestedDeny.setPeriod(new org.hl7.fhir.r4.model.Period()
                .setStartElement(new DateTimeType("2022-01-01"))
                .setEndElement(new DateTimeType("2022-12-31")));

        Consent.ProvisionComponent parentProvision = new Consent.ProvisionComponent();
        parentProvision.setProvision(List.of(nestedDeny));
        consent.setProvision(parentProvision);

        ConsentProvisions result = extractor.extractProvisionsPeriodByCode(consent, Set.of("code1"));

        assertThat(result.provisions()).hasSize(1);
        Provision p = result.provisions().getFirst();
        assertThat(p.code()).isEqualTo("code1");
        assertThat(p.period()).isEqualTo(Period.of(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31)));
        assertThat(p.permit()).isFalse();
    }

    @Test
    void ignoresProvisionWithNoCode() throws Exception {
        Consent consent = new Consent();
        consent.setId("consent-no-code");
        consent.setPatient(new Reference("Patient/123"));
        consent.setDateTimeElement(new DateTimeType("2023-01-01"));

        // Nested provision without any CodeableConcept
        Consent.ProvisionComponent nestedProvisionNoCode = new Consent.ProvisionComponent();
        nestedProvisionNoCode.setType(Consent.ConsentProvisionType.PERMIT);
        nestedProvisionNoCode.setPeriod(new org.hl7.fhir.r4.model.Period()
                .setStartElement(new DateTimeType("2022-01-01"))
                .setEndElement(new DateTimeType("2022-12-31")));
        // Note: no call to nestedProvisionNoCode.setCode(...)

        // Parent provision containing the nested one
        Consent.ProvisionComponent parentProvision = new Consent.ProvisionComponent();
        parentProvision.setProvision(List.of(nestedProvisionNoCode));
        consent.setProvision(parentProvision);

        ConsentProvisions result = extractor.extractProvisionsPeriodByCode(consent, Set.of("code1", "code2"));

        // No provisions should be extracted
        assertThat(result.provisions()).isEmpty();
    }


    @Test
    void throwsExceptionWhenNoConsentDate() {
        Consent consent = new Consent();
        consent.setId("consent-no-date");
        consent.setPatient(new Reference("Patient/123"));
        consent.setProvision(new Consent.ProvisionComponent());

        assertThatThrownBy(() -> extractor.extractProvisionsPeriodByCode(consent, Set.of("code1")))
                .isInstanceOf(ConsentViolatedException.class)
                .hasMessageContaining("consent-no-date has no valid consent date");
    }

    @Test
    void throwsExceptionWhenEmptyConsentDate() {
        Consent consent = new Consent();
        consent.setId("consent-empty-date");
        consent.setPatient(new Reference("Patient/123"));
        consent.setProvision(new Consent.ProvisionComponent());
        consent.setDateTimeElement(null);

        assertThatThrownBy(() -> extractor.extractProvisionsPeriodByCode(consent, Set.of("code1")))
                .isInstanceOf(ConsentViolatedException.class)
                .hasMessageContaining("consent-empty-date has no valid consent date");
    }

    @Test
    void throwsConsentViolatedException_whenConsentTimeIsEmpty() {
        Consent consent = new Consent();
        consent.setId("consent-empty-date");
        consent.setPatient(new Reference("Patient/123"));
        consent.setProvision(new Consent.ProvisionComponent());
        consent.setDateTimeElement(new DateTimeType()); // empty date triggers branch

        assertThatThrownBy(() ->
                extractor.extractProvisionsPeriodByCode(consent, Set.of("anyCode"))
        ).isInstanceOf(ConsentViolatedException.class)
                .hasMessageContaining("consent-empty-date has no valid consent date");
    }

    @Test
    void throwsConsentViolatedException_whenConsentTimeIsNull_withNestedProvision() {
        Consent consent = new Consent();
        consent.setId("consent-null-date");
        consent.setPatient(new Reference("Patient/123"));

        // Nested provision
        Consent.ProvisionComponent nestedPermit = new Consent.ProvisionComponent();
        nestedPermit.setType(Consent.ConsentProvisionType.PERMIT);
        nestedPermit.setCode(List.of(new CodeableConcept(new Coding("sys", "code1", "desc"))));
        nestedPermit.setPeriod(new org.hl7.fhir.r4.model.Period()
                .setStartElement(new DateTimeType("2022-01-01"))
                .setEndElement(new DateTimeType("2022-12-31")));

        Consent.ProvisionComponent parentProvision = new Consent.ProvisionComponent();
        parentProvision.setProvision(List.of(nestedPermit));
        consent.setProvision(parentProvision);

        consent.setDateTimeElement(null); // triggers exception

        assertThatThrownBy(() ->
                extractor.extractProvisionsPeriodByCode(consent, Set.of("code1"))
        ).isInstanceOf(ConsentViolatedException.class)
                .hasMessageContaining("consent-null-date has no valid consent date");
    }

    @Test
    void extractsNestedMultipleCodeableConceptsAndCodings() throws Exception {
        Consent consent = new Consent();
        consent.setId("consent-nested-multi-code");
        consent.setPatient(new Reference("Patient/123"));
        consent.setDateTimeElement(new DateTimeType("2023-01-01"));

        // Nested provision containing multiple CodeableConcepts
        Consent.ProvisionComponent nestedProvision = new Consent.ProvisionComponent();
        nestedProvision.setType(Consent.ConsentProvisionType.PERMIT);

        // Set a valid period for the nested provision
        nestedProvision.setPeriod(new org.hl7.fhir.r4.model.Period()
                .setStartElement(new DateTimeType("2022-01-01"))
                .setEndElement(new DateTimeType("2022-12-31")));

        // First CodeableConcept with 2 codings
        CodeableConcept cc1 = new CodeableConcept();
        cc1.addCoding(new Coding("sys", "code1", "desc1"));
        cc1.addCoding(new Coding("sys", "code2", "desc2"));

        // Second CodeableConcept with 1 coding
        CodeableConcept cc2 = new CodeableConcept();
        cc2.addCoding(new Coding("sys", "code3", "desc3"));

        nestedProvision.setCode(List.of(cc1, cc2));

        // Parent provision that contains the nested one
        Consent.ProvisionComponent parentProvision = new Consent.ProvisionComponent();
        parentProvision.setProvision(List.of(nestedProvision));

        consent.setProvision(parentProvision);

        Set<String> requiredCodes = Set.of("code1", "code2", "code3", "codeX");

        ConsentProvisions result = extractor.extractProvisionsPeriodByCode(consent, requiredCodes);

        // Should extract all 3 codes from the nested provision
        assertThat(result.provisions()).hasSize(3);

        // Verify extracted codes
        assertThat(result.provisions())
                .extracting(Provision::code)
                .containsExactlyInAnyOrder("code1", "code2", "code3");

        // Verify all periods are the same
        result.provisions().forEach(p ->
                assertThat(p.period()).isEqualTo(Period.of(LocalDate.of(2022, 1, 1), LocalDate.of(2022, 12, 31)))
        );

        // All should be permits
        result.provisions().forEach(p -> assertThat(p.permit()).isTrue());
    }


    @Test
    void throwsConsentViolatedException_whenConsentTimeIsEmpty_withNestedProvision() {
        Consent consent = new Consent();
        consent.setId("consent-empty-date");
        consent.setPatient(new Reference("Patient/123"));

        // Nested provision
        Consent.ProvisionComponent nestedDeny = new Consent.ProvisionComponent();
        nestedDeny.setType(Consent.ConsentProvisionType.DENY);
        nestedDeny.setCode(List.of(new CodeableConcept(new Coding("sys", "code2", ""))));
        nestedDeny.setPeriod(new org.hl7.fhir.r4.model.Period()
                .setStartElement(new DateTimeType("2022-01-01"))
                .setEndElement(new DateTimeType("2022-12-31")));

        Consent.ProvisionComponent parentProvision = new Consent.ProvisionComponent();
        parentProvision.setProvision(List.of(nestedDeny));
        consent.setProvision(parentProvision);

        consent.setDateTimeElement(new DateTimeType()); // empty date triggers branch

        assertThatThrownBy(() ->
                extractor.extractProvisionsPeriodByCode(consent, Set.of("code2"))
        ).isInstanceOf(ConsentViolatedException.class)
                .hasMessageContaining("consent-empty-date has no valid consent date");
    }

    @Test
    void throwsConsentViolatedException_whenConsentTimeIsNull() {
        Consent consent = new Consent();
        consent.setId("consent-null-date");
        consent.setPatient(new Reference("Patient/123"));
        consent.setProvision(new Consent.ProvisionComponent());

        assertThatThrownBy(() ->
                extractor.extractProvisionsPeriodByCode(consent, Set.of("anyCode"))
        ).isInstanceOf(ConsentViolatedException.class)
                .hasMessageContaining("consent-null-date has no valid consent date");
    }


}
