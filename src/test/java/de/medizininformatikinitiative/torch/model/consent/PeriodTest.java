package de.medizininformatikinitiative.torch.model.consent;

import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.TimeType;
import org.hl7.fhir.r4.model.Timing;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class PeriodTest {

    @Test
    void testIntersect_noOverlap() {
        Period p1 = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));
        Period p2 = Period.of(LocalDate.of(2025, 1, 11), LocalDate.of(2025, 1, 20));

        assertThat(p1.intersect(p2)).isNull();
        assertThat(p2.intersect(p1)).isNull();
    }

    @Test
    void testIntersect_partialOverlap() {
        Period p1 = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));
        Period p2 = Period.of(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 15));

        Period intersection = p1.intersect(p2);
        assertThat(intersection).isNotNull();
        assertThat(intersection.start()).isEqualTo(LocalDate.of(2025, 1, 5));
        assertThat(intersection.end()).isEqualTo(LocalDate.of(2025, 1, 10));
    }

    @Test
    void testIntersect_fullOverlap() {
        Period p1 = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));
        Period p2 = Period.of(LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 9));

        Period intersection = p1.intersect(p2);
        assertThat(intersection).isNotNull();
        assertThat(intersection.start()).isEqualTo(LocalDate.of(2025, 1, 2));
        assertThat(intersection.end()).isEqualTo(LocalDate.of(2025, 1, 9));
    }

    @Test
    void testSubtract_noOverlap() {
        Period p1 = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));
        Period deny = Period.of(LocalDate.of(2025, 1, 11), LocalDate.of(2025, 1, 15));

        List<Period> result = p1.subtract(deny);
        assertThat(result).hasSize(1).containsExactly(p1);
    }

    @Test
    void testSubtract_partialLeft() {
        Period p1 = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));
        Period deny = Period.of(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 7));

        List<Period> result = p1.subtract(deny);
        assertThat(result).containsExactly(
                Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 4)),
                Period.of(LocalDate.of(2025, 1, 8), LocalDate.of(2025, 1, 10))
        );
    }

    @Test
    void testSubtract_leftEdge() {
        Period p1 = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));
        Period deny = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 5));

        List<Period> result = p1.subtract(deny);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(Period.of(LocalDate.of(2025, 1, 6), LocalDate.of(2025, 1, 10)));
    }

    @Test
    void testSubtract_rightEdge() {
        Period p1 = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));
        Period deny = Period.of(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 10));

        List<Period> result = p1.subtract(deny);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isEqualTo(Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 4)));
    }

    @Test
    void testSubtract_fullCover() {
        Period p1 = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));
        Period deny = Period.of(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 10));

        List<Period> result = p1.subtract(deny);
        assertThat(result).isEmpty();
    }


    @Nested
    class FromHapiTests {

        @Test
        void testFromHapiPeriod() {
            org.hl7.fhir.r4.model.Period hapi = new org.hl7.fhir.r4.model.Period();
            hapi.setStartElement(new DateTimeType("2025-01-01"));
            hapi.setEndElement(new DateTimeType("2025-01-10"));

            Period period = Period.fromHapi(hapi);
            assertThat(period.start()).isEqualTo(LocalDate.of(2025, 1, 1));
            assertThat(period.end()).isEqualTo(LocalDate.of(2025, 1, 10));
        }

        @Test
        void testFromHapiDateTimeType() {
            DateTimeType dt = new DateTimeType("2025-01-05T12:00:00Z");
            Period period = Period.fromHapi(dt);
            assertThat(period.start()).isEqualTo(LocalDate.of(2025, 1, 5));
            assertThat(period.end()).isEqualTo(LocalDate.of(2025, 1, 5));
        }


        @Test
        void testFromHapiTimeType() {
            TimeType t = new TimeType("12:00:00Z");
            assertThatThrownBy(() -> Period.fromHapi(t))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported FHIR time type");
        }

        @Test
        void testFromHapiDateType() {
            DateType d = new DateType("2025-01-07");
            Period period = Period.fromHapi(new DateTimeType(d.getValue()));
            assertThat(period.start()).isEqualTo(LocalDate.of(2025, 1, 7));
            assertThat(period.end()).isEqualTo(LocalDate.of(2025, 1, 7));
        }

        @Test
        void testFromHapiInstantType() {
            InstantType i = new InstantType("2025-01-08T15:30:00Z");
            Period period = Period.fromHapi(new DateTimeType(i.getValue()));
            assertThat(period.start()).isEqualTo(LocalDate.of(2025, 1, 8));
            assertThat(period.end()).isEqualTo(LocalDate.of(2025, 1, 8));
        }

        @Test
        void testFromHapiTiming_throws() {
            Timing t = new Timing();
            assertThatThrownBy(() -> Period.fromHapi(t))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported FHIR time type");
        }

        @Test
        void testFromHapiUnsupported_throws() {
            Resource r = new Patient(); // not a temporal type
            assertThatThrownBy(() -> Period.fromHapi(r))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported FHIR type");
        }
    }

}
