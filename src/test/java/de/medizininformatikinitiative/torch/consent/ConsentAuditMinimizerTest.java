package de.medizininformatikinitiative.torch.consent;

import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentAuditMinimizerTest {

    @Test
    void minimizeConsent_keepsOnlyRelevantFields() {
        Consent source = new Consent();
        source.setId("consent-1");
        Meta meta = new Meta();
        meta.addProfile("https://example.org/consent-profile");
        meta.setVersionId("42");
        source.setMeta(meta);
        source.setPatient(new Reference("Patient/1"));
        source.getProvision().setId("root-provision");
        source.setDateTimeElement(new DateTimeType("2022-09-09T17:05:07+02:00"));
        source.setStatus(Consent.ConsentState.ACTIVE);
        source.addIdentifier(new Identifier().setValue("should-be-dropped"));

        Consent minimized = ConsentAuditMinimizer.minimize(source);

        assertThat(minimized.getIdPart()).isEqualTo("consent-1");
        assertThat(minimized.getMeta().getProfile()).extracting(CanonicalType::getValue)
                .containsExactly("https://example.org/consent-profile");
        assertThat(minimized.getMeta().getVersionId()).isNullOrEmpty();
        assertThat(minimized.getPatient().getReference()).isEqualTo("Patient/1");
        assertThat(minimized.getProvision().getId()).isEqualTo("root-provision");
        assertThat(minimized.getDateTimeElement().getValueAsString()).isEqualTo(source.getDateTimeElement().getValueAsString());
        assertThat(minimized.getStatus()).isEqualTo(Consent.ConsentState.ACTIVE);
        assertThat(minimized.getIdentifier()).isEmpty();
    }

    @Test
    void minimizeEncounter_keepsOnlyRelevantFields() {
        Encounter source = new Encounter();
        source.setId("encounter-1");
        Meta meta = new Meta();
        meta.addProfile("https://example.org/encounter-profile");
        source.setMeta(meta);
        source.setSubject(new Reference("Patient/1"));
        Period period = new Period();
        period.setStartElement(new DateTimeType("2022-02-19T09:04:30+01:00"));
        period.setEndElement(new DateTimeType("2022-02-19T12:15:00+01:00"));
        source.setPeriod(period);
        source.setType(List.of(new CodeableConcept().setText("should-be-dropped")));

        Encounter minimized = ConsentAuditMinimizer.minimize(source);

        assertThat(minimized.getIdPart()).isEqualTo("encounter-1");
        assertThat(minimized.getMeta().getProfile()).extracting(CanonicalType::getValue)
                .containsExactly("https://example.org/encounter-profile");
        assertThat(minimized.getSubject().getReference()).isEqualTo("Patient/1");
        assertThat(minimized.getPeriod().getStartElement().getValueAsString())
                .isEqualTo(source.getPeriod().getStartElement().getValueAsString());
        assertThat(minimized.getType()).isEmpty();
    }
}
