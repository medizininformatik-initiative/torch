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

            Period period = Period.fromHapi(hapi).get();

            assertThat(period.start()).isEqualTo(LocalDate.of(2025, 1, 1));
            assertThat(period.end()).isEqualTo(LocalDate.of(2025, 1, 10));
        }

        @Test
        void testFromHapiPeriod_ignoredWhenMissingStart() {
            org.hl7.fhir.r4.model.Period hapi = new org.hl7.fhir.r4.model.Period();
            hapi.setEndElement(new DateTimeType("2025-01-10"));

            assertThat(Period.fromHapi(hapi)).isEmpty();
        }

        @Test
        void testFromHapiPeriod_ignoredWhenMissingEnd() {
            org.hl7.fhir.r4.model.Period hapi = new org.hl7.fhir.r4.model.Period();
            hapi.setStartElement(new DateTimeType("2025-01-01"));

            assertThat(Period.fromHapi(hapi)).isEmpty();
        }

        @Test
        void testFromHapiDateTimeType() {
            DateTimeType dt = new DateTimeType("2025-01-05T12:00:00Z");

            Period period = Period.fromHapi(dt).get();

            assertThat(period.start()).isEqualTo(LocalDate.of(2025, 1, 5));
            assertThat(period.end()).isEqualTo(LocalDate.of(2025, 1, 5));
        }

        @Test
        void testFromHapiDateType() {
            DateType d = new DateType("2025-01-07");

            Period period = Period.fromHapi(d).get();

            assertThat(period.start()).isEqualTo(LocalDate.of(2025, 1, 7));
            assertThat(period.end()).isEqualTo(LocalDate.of(2025, 1, 7));
        }

        @Test
        void testFromHapiInstantType() {
            InstantType i = new InstantType("2025-01-08T15:30:00Z");

            Period period = Period.fromHapi(i).get();

            assertThat(period.start()).isEqualTo(LocalDate.of(2025, 1, 8));
            assertThat(period.end()).isEqualTo(LocalDate.of(2025, 1, 8));
        }

        @Test
        void testFromHapiTimeType() {
            TimeType t = new TimeType("12:00:00Z");

            assertThat(Period.fromHapi(t)).isEmpty();
        }

        @Test
        void testFromHapiTimingIgnored() {
            Timing t = new Timing();

            assertThat(Period.fromHapi(t)).isEmpty();
        }

        @Test
        void testFromHapiDateType_ignoredWhenNoValue() {
            DateType d = new DateType();

            assertThat(Period.fromHapi(d)).isEmpty();
        }

        @Test
        void testFromHapiDateType_ignoredWhenPrecisionLessThanDay_yearOnly() {
            // "2025" parses as YEAR precision in HAPI
            DateType d = new DateType("2025");

            assertThat(Period.fromHapi(d)).isEmpty();
        }

        @Test
        void testFromHapiDateTimeType_ignoredWhenNoValue() {
            DateTimeType dt = new DateTimeType(); // hasValue() == false

            assertThat(Period.fromHapi(dt)).isEmpty();
        }

        @Test
        void testFromHapiUnsupportedIgnored() {
            Resource r = new Patient(); // not a temporal type

            assertThat(Period.fromHapi(r)).isEmpty();
        }

        @Test
        void testFromHapiNullIgnored() {
            assertThat(Period.fromHapi(null)).isEmpty();
        }

        @Test
        void testFromHapiInstantType_ignoredWhenNoValue() {
            InstantType i = new InstantType(); // no value -> hasValue() == false

            assertThat(Period.fromHapi(i)).isEmpty();
        }

        @Test
        void testFromHapiDateTimeType_ignoredWhenPrecisionLessThanDay_yearOnly() {
            // "2025" parses as YEAR precision
            DateTimeType dt = new DateTimeType("2025");

            assertThat(Period.fromHapi(dt)).isEmpty();
        }

        @Test
        void testFromHapiDateTimeType_ignoredWhenPrecisionLessThanDay_monthOnly() {
            // "2025-01" parses as MONTH precision
            DateTimeType dt = new DateTimeType("2025-01");

            assertThat(Period.fromHapi(dt)).isEmpty();
        }

        @Test
        void testFromHapiDateType_ignoredWhenPrecisionLessThanDay_monthOnly() {
            // "2025-01" parses as MONTH precision
            DateType d = new DateType("2025-01");

            assertThat(Period.fromHapi(d)).isEmpty();
        }

        @Test
        void testFromHapiPeriod_ignoredWhenStartPrecisionLessThanDay() {
            org.hl7.fhir.r4.model.Period hapi = new org.hl7.fhir.r4.model.Period();
            hapi.setStartElement(new DateTimeType("2025"));
            hapi.setEndElement(new DateTimeType("2025-01-10"));

            assertThat(Period.fromHapi(hapi)).isEmpty();
        }

        @Test
        void testFromHapiPeriod_ignoredWhenEndPrecisionLessThanDay() {
            org.hl7.fhir.r4.model.Period hapi = new org.hl7.fhir.r4.model.Period();
            hapi.setStartElement(new DateTimeType("2025-01-01"));
            hapi.setEndElement(new DateTimeType("2025-01"));

            assertThat(Period.fromHapi(hapi)).isEmpty();
        }

    }
}
