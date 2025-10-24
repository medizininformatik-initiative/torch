package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.consent.ConsentProvisions;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.consent.Provision;
import de.medizininformatikinitiative.torch.model.management.TermCode;
import org.hl7.fhir.r4.model.DateTimeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ConsentCalculatorTest {

    public static final TermCode someCode = new TermCode("s1", "someConsentKey");
    public static final TermCode CODE_1 = new TermCode("s1", "code1");
    public static final TermCode CODE_2 = new TermCode("s1", "code2");
    private ConsentCalculator calculator;

    private static Period p(String start, String end) {
        return Period.of(LocalDate.parse(start), LocalDate.parse(end));
    }

    @Nested
    class CalculateConsentPeriodsByCodeTests {
        @BeforeEach
        void setUp() {
            calculator = new ConsentCalculator();
        }

        @Test
        void irrelevantCodesAreSkipped() {
            calculator = new ConsentCalculator();

            // Provision has an irrelevant code "otherCode"
            Provision irrelevant = new Provision(new TermCode("s1", "otherCode"), p("2024-01-01", "2024-01-31"), true);
            ConsentProvisions cp = new ConsentProvisions("patient1", null, List.of(irrelevant));

            Map<TermCode, NonContinuousPeriod> result = calculator.subtractAndMergeByCode(
                    List.of(cp),
                    Set.of(someCode)
            );

            // "otherCode" should be skipped → result should be empty
            assertThat(result).isEmpty();
        }

        @Test
        void permitAndDenyInSameConsent() {
            // Permit for code1 from Jan 1 to Jan 31
            Provision permit = new Provision(someCode, p("2024-01-01", "2024-01-31"), true);
            // Deny for code1 from Jan 10 to Jan 20
            Provision deny = new Provision(someCode, p("2024-01-10", "2024-01-20"), false);

            // Both in the same ConsentProvisions
            ConsentProvisions cp = new ConsentProvisions(
                    "patient1",
                    new DateTimeType("2024-01-01T00:00:00Z"),
                    List.of(permit, deny)
            );

            Map<TermCode, NonContinuousPeriod> result = calculator.subtractAndMergeByCode(
                    List.of(cp),
                    Set.of(someCode)
            );

            NonContinuousPeriod periods = result.get(someCode);

            // Should split around the deny period: Jan 1–9 and Jan 21–31
            assertThat(periods.periods()).containsExactly(
                    p("2024-01-01", "2024-01-09"),
                    p("2024-01-21", "2024-01-31")
            );
        }


        @Test
        void withPermitsAndDenies() {
            // Older consent: permit code1 from 2024-01-01 to 2024-01-31
            Provision permitOld = new Provision(CODE_1, p("2024-01-01", "2024-01-31"), true);
            // Newer consent: deny code1 from 2024-01-10 to 2024-01-20
            Provision denyNew = new Provision(CODE_1, p("2024-01-10", "2024-01-20"), false);
            // Another permit for code2
            Provision permitCode2 = new Provision(CODE_2, p("2024-02-01", "2024-02-28"), true);

            ConsentProvisions old = new ConsentProvisions(
                    "patient1",
                    new DateTimeType("2024-01-01T00:00:00Z"),
                    List.of(permitOld)
            );

            ConsentProvisions newer = new ConsentProvisions(
                    "patient1",
                    new DateTimeType("2024-01-10T00:00:00Z"),
                    List.of(denyNew)
            );

            ConsentProvisions cp2 = new ConsentProvisions(
                    "patient1",
                    new DateTimeType("2024-02-01T00:00:00Z"),
                    List.of(permitCode2)
            );

            Map<TermCode, NonContinuousPeriod> result = calculator.subtractAndMergeByCode(
                    List.of(old, newer, cp2),
                    Set.of(CODE_1, CODE_2)
            );

            // code1 should have periods before 2024-01-10 and after 2024-01-20
            NonContinuousPeriod code1Periods = result.get(CODE_1);
            assertThat(code1Periods.periods()).containsExactly(
                    p("2024-01-01", "2024-01-09"),
                    p("2024-01-21", "2024-01-31")
            );

            // code2 should remain as is
            NonContinuousPeriod code2Periods = result.get(CODE_2);
            assertThat(code2Periods.periods()).containsExactly(
                    p("2024-02-01", "2024-02-28")
            );
        }

        @Test
        void deniedWithoutPermit() {
            Provision deny = new Provision(CODE_1, p("2024-03-01", "2024-03-10"), false);
            ConsentProvisions cp = new ConsentProvisions("patient1", null, List.of(deny));

            Map<TermCode, NonContinuousPeriod> result = calculator.subtractAndMergeByCode(
                    List.of(cp),
                    Set.of(CODE_1)
            );

            // Deny with no existing permit should result in empty periods
            assertThat(result).isEmpty();
        }

    }

    @Nested
    class IntersectConsentTests {
        @BeforeEach
        void setUp() {
            calculator = new ConsentCalculator();
        }


        @Test
        void intersectConsent_multipleCodes_overlap() throws ConsentViolatedException {
            Map<TermCode, NonContinuousPeriod> consentsByCode = Map.of(
                    CODE_1, new NonContinuousPeriod(List.of(p("2024-01-01", "2024-01-10"))),
                    CODE_2, new NonContinuousPeriod(List.of(p("2024-01-05", "2024-01-15")))
            );

            NonContinuousPeriod intersected = calculator.intersectConsent(consentsByCode);

            assertThat(intersected.periods())
                    .containsExactly(p("2024-01-05", "2024-01-10"));
        }

        @Test
        void intersectConsent_multipleCodes_noOverlap_throws() {
            Map<TermCode, NonContinuousPeriod> consentsByCode = Map.of(
                    CODE_1, new NonContinuousPeriod(List.of(p("2024-01-01", "2024-01-10"))),
                    CODE_2, new NonContinuousPeriod(List.of(p("2024-01-11", "2024-01-20")))
            );

            assertThatThrownBy(() -> calculator.intersectConsent(consentsByCode))
                    .isInstanceOf(ConsentViolatedException.class)
                    .hasMessageContaining("Consent periods do not overlap");
        }

        @Test
        void intersectConsent_emptyMap_throws() {
            assertThatThrownBy(() -> calculator.intersectConsent(Map.of()))
                    .isInstanceOf(ConsentViolatedException.class)
                    .hasMessageContaining("No consent periods found");
        }
    }

    @Nested
    class CalculateConsentTests {
        @BeforeEach
        void setUp() {
            calculator = new ConsentCalculator();
        }


        @Test
        void multiplePatientsSomeViolated() {
            Provision permit1 = new Provision(CODE_1, p("2024-01-01", "2024-01-10"), true);
            Provision permit2 = new Provision(CODE_2, p("2024-01-05", "2024-01-15"), true);

            ConsentProvisions cp1 = new ConsentProvisions("patient1", null, List.of(permit1, permit2));

            Provision permit3 = new Provision(CODE_1, p("2024-02-01", "2024-02-10"), true);
            Provision permit4 = new Provision(CODE_2, p("2024-02-11", "2024-02-20"), true);

            ConsentProvisions cp2 = new ConsentProvisions("patient2", null, List.of(permit3, permit4));

            Map<String, List<ConsentProvisions>> consentsByPatient = Map.of(
                    "patient1", List.of(cp1),
                    "patient2", List.of(cp2)
            );

            Map<String, NonContinuousPeriod> result = calculator.calculateConsent(Set.of(CODE_1, CODE_2), consentsByPatient);

            // patient1 has overlap between code1 and code2 → intersection 2024-01-05 to 2024-01-10
            assertThat(result.get("patient1").periods()).containsExactly(p("2024-01-05", "2024-01-10"));

            // patient2 has no overlap → should not appear in result map
            assertThat(result.containsKey("patient2")).isFalse();
        }

        @Test
        void singlePatientFullOverlap() {
            Provision permit1 = new Provision(CODE_1, p("2024-01-01", "2024-01-10"), true);
            Provision permit2 = new Provision(CODE_2, p("2024-01-01", "2024-01-15"), true);

            ConsentProvisions cp = new ConsentProvisions("patient1", null, List.of(permit1, permit2));

            Map<String, NonContinuousPeriod> result = calculator.calculateConsent(
                    Set.of(CODE_1, CODE_2),
                    Map.of("patient1", List.of(cp))
            );

            // intersection should be 2024-01-01 to 2024-01-10
            assertThat(result.get("patient1").periods()).containsExactly(p("2024-01-01", "2024-01-10"));
        }
    }


}

