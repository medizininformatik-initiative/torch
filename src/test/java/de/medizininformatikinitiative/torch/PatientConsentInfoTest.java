package de.medizininformatikinitiative.torch;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.model.consent.Period;
import org.hl7.fhir.r4.model.Condition;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PatientConsentInfoTest {

    @Nested
    class PatientConsentInfo {

        @Test
        void updateConsentPeriodsByPatientEncounters() {
        }

        @Test
        void patientId() {
        }

        @Test
        void periods() {
        }

    }

    @Nested
    class PeriodTest {
        FhirContext ctx = FhirContext.forR4();

        @Test
        void fromHapiPeriod() {
            Condition condition = (Condition) ctx.newJsonParser().parseResource("""
                    {
                      "resourceType": "Condition",
                      "onsetPeriod": {
                        "start": "2020-02-26T12:00:00+01:00",
                        "end": "2020-03-05T13:00:00+01:00"
                      }
                    }""");

            Period result = Period.fromHapi(condition.getOnsetPeriod());

            assertThat(result).isEqualTo(Period.of(LocalDate.parse("2020-02-26"), LocalDate.parse("2020-03-05")));
        }

        @Test
        void fromHapiDateTime() {
            Condition condition = (Condition) ctx.newJsonParser().parseResource("""
                    {
                      "resourceType": "Condition",
                      "onsetDateTime": "2019-01-20T12:00:00+01:00"
                    }""");

            Period result = Period.fromHapi(condition.getOnsetDateTimeType());

            assertThat(result).isEqualTo(Period.of(LocalDate.parse("2019-01-20"), LocalDate.parse("2019-01-20")));
        }
    }
}