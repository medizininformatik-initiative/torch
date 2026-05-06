package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException;
import de.medizininformatikinitiative.torch.model.consent.ConsentCodeConfig;
import de.medizininformatikinitiative.torch.model.consent.ConsentProvisions;
import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import de.medizininformatikinitiative.torch.model.consent.Period;
import de.medizininformatikinitiative.torch.model.consent.ProspectiveEntry;
import de.medizininformatikinitiative.torch.model.consent.Provision;
import de.medizininformatikinitiative.torch.model.consent.RetroModifier;
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
    public static final TermCode GATE_CODE = new TermCode("s1", "gateCode");

    public static final TermCode PROSPECTIVE = new TermCode("s1", "prospective");
    public static final TermCode RETRO = new TermCode("s1", "retro");
    private static final LocalDate LOOKBACK_DATE = LocalDate.of(1900, 1, 1);

    private ConsentCalculator calculator;

    private static Period p(String start, String end) {
        return Period.of(LocalDate.parse(start), LocalDate.parse(end));
    }

    private static ConsentCodeConfig noRetroConfig() {
        return new ConsentCodeConfig(List.of());
    }

    private static ConsentCodeConfig retroConfig() {
        return new ConsentCodeConfig(List.of(
                new ProspectiveEntry(PROSPECTIVE, false, List.of(), List.of(new RetroModifier(RETRO)), LOOKBACK_DATE)
        ));
    }

    private static ConsentCodeConfig dataCodesConfig() {
        return new ConsentCodeConfig(List.of(
                new ProspectiveEntry(CODE_1, false, List.of(), List.of(), null),
                new ProspectiveEntry(CODE_2, false, List.of(), List.of(), null)
        ));
    }

    @Nested
    class CalculateConsentPeriodsByCodeTests {
        @BeforeEach
        void setUp() {
            calculator = new ConsentCalculator(noRetroConfig());
        }

        @Test
        void irrelevantCodesAreSkipped() {
            Provision irrelevant = new Provision(new TermCode("s1", "otherCode"), p("2024-01-01", "2024-01-31"), true);
            ConsentProvisions cp = new ConsentProvisions("patient1", null, List.of(irrelevant));

            Map<TermCode, NonContinuousPeriod> result = calculator.subtractAndMergeByCode(
                    List.of(cp),
                    Set.of(someCode)
            );

            assertThat(result).isEmpty();
        }

        @Test
        void permitAndDenyInSameConsent() {
            Provision permit = new Provision(someCode, p("2024-01-01", "2024-01-31"), true);
            Provision deny = new Provision(someCode, p("2024-01-10", "2024-01-20"), false);

            ConsentProvisions cp = new ConsentProvisions(
                    "patient1",
                    new DateTimeType("2024-01-01T00:00:00Z"),
                    List.of(permit, deny)
            );

            Map<TermCode, NonContinuousPeriod> result = calculator.subtractAndMergeByCode(
                    List.of(cp),
                    Set.of(someCode)
            );

            assertThat(result.get(someCode).periods()).containsExactly(
                    p("2024-01-01", "2024-01-09"),
                    p("2024-01-21", "2024-01-31")
            );
        }

        @Test
        void codesFromDifferentResourcesAreNotCombined() {
            ConsentProvisions cp1 = new ConsentProvisions("patient1", null,
                    List.of(new Provision(CODE_1, p("2024-01-01", "2024-01-31"), true)));
            ConsentProvisions cp2 = new ConsentProvisions("patient1", null,
                    List.of(new Provision(CODE_2, p("2024-02-01", "2024-02-28"), true)));

            Map<TermCode, NonContinuousPeriod> result = calculator.subtractAndMergeByCode(
                    List.of(cp1, cp2),
                    Set.of(CODE_1, CODE_2)
            );

            assertThat(result).isEmpty();
        }

        @Test
        void denyFromSeparateResourceIsApplied() {
            Provision permit1 = new Provision(CODE_1, p("2024-01-01", "2024-01-31"), true);
            Provision permit2 = new Provision(CODE_2, p("2024-02-01", "2024-02-28"), true);
            Provision deny = new Provision(CODE_1, p("2024-01-10", "2024-01-20"), false);

            ConsentProvisions complete = new ConsentProvisions("patient1",
                    new DateTimeType("2024-01-01T00:00:00Z"),
                    List.of(permit1, permit2));
            ConsentProvisions revocation = new ConsentProvisions("patient1",
                    new DateTimeType("2024-01-10T00:00:00Z"),
                    List.of(deny));

            Map<TermCode, NonContinuousPeriod> result = calculator.subtractAndMergeByCode(
                    List.of(complete, revocation),
                    Set.of(CODE_1, CODE_2)
            );

            assertThat(result.get(CODE_1).periods()).containsExactly(
                    p("2024-01-01", "2024-01-09"),
                    p("2024-01-21", "2024-01-31")
            );
            assertThat(result.get(CODE_2).periods()).containsExactly(
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

            assertThat(result).isEmpty();
        }

        @Test
        void fullyDeniedPermitResultsInEmptyMap() {
            Provision permit = new Provision(CODE_1, p("2024-01-01", "2024-01-31"), true);
            Provision deny = new Provision(CODE_1, p("2024-01-01", "2024-01-31"), false);
            ConsentProvisions cp = new ConsentProvisions("patient1", null, List.of(permit, deny));

            Map<TermCode, NonContinuousPeriod> result = calculator.subtractAndMergeByCode(
                    List.of(cp),
                    Set.of(CODE_1)
            );

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class RetroModifierTests {
        @BeforeEach
        void setUp() {
            calculator = new ConsentCalculator(retroConfig());
        }

        @Test
        void retroInSeparateResourceDoesNotShiftProspective() {
            Provision prospectivePermit = new Provision(PROSPECTIVE, p("2023-01-01", "2025-12-31"), true);
            Provision retroPermit = new Provision(RETRO, p("2020-01-01", "2025-12-31"), true);
            ConsentProvisions cpProspective = new ConsentProvisions("patient1", null, List.of(prospectivePermit));
            ConsentProvisions cpRetro = new ConsentProvisions("patient1", null, List.of(retroPermit));

            Map<TermCode, NonContinuousPeriod> result = calculator.subtractAndMergeByCode(
                    List.of(cpProspective, cpRetro), Set.of(PROSPECTIVE)
            );

            assertThat(result.get(PROSPECTIVE).periods()).containsExactly(p("2023-01-01", "2025-12-31"));
        }

        @Test
        void retroPermitShiftsProspectiveStartToLookback() {
            Provision prospectivePermit = new Provision(PROSPECTIVE, p("2023-01-01", "2025-12-31"), true);
            Provision retroPermit = new Provision(RETRO, p("2020-01-01", "2025-12-31"), true);
            ConsentProvisions cp = new ConsentProvisions("patient1", null, List.of(prospectivePermit, retroPermit));

            Map<TermCode, NonContinuousPeriod> result = calculator.subtractAndMergeByCode(
                    List.of(cp), Set.of(PROSPECTIVE)
            );

            assertThat(result.get(PROSPECTIVE).get(0).start()).isEqualTo(LOOKBACK_DATE);
            assertThat(result.get(PROSPECTIVE).get(0).end()).isEqualTo(LocalDate.parse("2025-12-31"));
        }

        @Test
        void retroPermitWithNoOverlapLeavesProspectiveUnchanged() {
            Provision prospectivePermit = new Provision(PROSPECTIVE, p("2020-01-01", "2024-12-31"), true);
            Provision retroPermit = new Provision(RETRO, p("2025-01-01", "2030-12-31"), true);
            ConsentProvisions cp = new ConsentProvisions("patient1", null, List.of(prospectivePermit, retroPermit));

            Map<TermCode, NonContinuousPeriod> result = calculator.subtractAndMergeByCode(
                    List.of(cp), Set.of(PROSPECTIVE)
            );

            assertThat(result.get(PROSPECTIVE).periods()).containsExactly(p("2020-01-01", "2024-12-31"));
        }

        @Test
        void retroDenyDoesNotShiftProspective() {
            Provision prospectivePermit = new Provision(PROSPECTIVE, p("2020-01-01", "2025-12-31"), true);
            Provision retroDeny = new Provision(RETRO, p("2020-01-01", "2025-12-31"), false);
            ConsentProvisions cp = new ConsentProvisions("patient1", null, List.of(prospectivePermit, retroDeny));

            Map<TermCode, NonContinuousPeriod> result = calculator.subtractAndMergeByCode(
                    List.of(cp), Set.of(PROSPECTIVE)
            );

            assertThat(result.get(PROSPECTIVE).periods()).containsExactly(p("2020-01-01", "2025-12-31"));
        }

        @Test
        void retroCodeIsDroppedFromResult() {
            Provision retroPermit = new Provision(RETRO, p("2020-01-01", "2025-12-31"), true);
            Provision prospectivePermit = new Provision(PROSPECTIVE, p("2020-01-01", "2025-12-31"), true);
            ConsentProvisions cp = new ConsentProvisions("patient1", null, List.of(retroPermit, prospectivePermit));

            Map<TermCode, NonContinuousPeriod> result = calculator.subtractAndMergeByCode(
                    List.of(cp), Set.of(PROSPECTIVE)
            );

            assertThat(result).doesNotContainKey(RETRO);
        }

        @Test
        void denyDoesNotPunchHoleInRetroExtendedPermit() {
            Provision prospectivePermit = new Provision(PROSPECTIVE, p("2020-01-01", "2025-12-31"), true);
            Provision prospectiveDeny = new Provision(PROSPECTIVE, p("2022-01-01", "2022-12-31"), false);
            Provision retroPermit = new Provision(RETRO, p("2020-01-01", "2025-12-31"), true);
            ConsentProvisions cp = new ConsentProvisions("patient1", null,
                    List.of(prospectivePermit, prospectiveDeny, retroPermit));

            Map<TermCode, NonContinuousPeriod> result = calculator.subtractAndMergeByCode(
                    List.of(cp), Set.of(PROSPECTIVE)
            );

            assertThat(result.get(PROSPECTIVE).periods()).containsExactly(
                    p(LOOKBACK_DATE.toString(), "2025-12-31")
            );
        }

        @Test
        void prospectiveEntryWithNoRetroModifiersIsUnchanged() {
            TermCode plain = new TermCode("s1", "plain");
            ConsentCodeConfig cfg = new ConsentCodeConfig(List.of(
                    new ProspectiveEntry(PROSPECTIVE, false, List.of(), List.of(new RetroModifier(RETRO)), LOOKBACK_DATE),
                    new ProspectiveEntry(plain, false, List.of(), List.of(), null)
            ));
            ConsentCalculator calc = new ConsentCalculator(cfg);

            Provision plainPermit = new Provision(plain, p("2023-01-01", "2025-12-31"), true);
            ConsentProvisions cp = new ConsentProvisions("patient1", null, List.of(plainPermit));

            Map<TermCode, NonContinuousPeriod> result = calc.subtractAndMergeByCode(
                    List.of(cp), Set.of(plain));

            assertThat(result.get(plain).periods()).containsExactly(p("2023-01-01", "2025-12-31"));
        }

        @Test
        void unknownCodeNotInConfigPassesThroughUnchanged() {
            TermCode unknown = new TermCode("s1", "unknown");
            Provision unknownPermit = new Provision(unknown, p("2023-01-01", "2025-12-31"), true);
            Provision retroPermit = new Provision(RETRO, p("2020-01-01", "2025-12-31"), true);
            ConsentProvisions cp = new ConsentProvisions("patient1", null, List.of(unknownPermit, retroPermit));

            Map<TermCode, NonContinuousPeriod> result = calculator.subtractAndMergeByCode(
                    List.of(cp), Set.of(unknown));

            assertThat(result.get(unknown).periods()).containsExactly(p("2023-01-01", "2025-12-31"));
        }

        @Test
        void retroModifierDenyPunchesHoleInRetroExtendedPermit() {
            Provision prospectivePermit = new Provision(PROSPECTIVE, p("2023-01-01", "2025-12-31"), true);
            Provision retroPermit = new Provision(RETRO, p("2020-01-01", "2025-12-31"), true);
            Provision retroDeny = new Provision(RETRO, p("1900-01-01", "2019-12-31"), false);
            ConsentProvisions cp = new ConsentProvisions("patient1", null,
                    List.of(prospectivePermit, retroPermit, retroDeny));

            Map<TermCode, NonContinuousPeriod> result = calculator.subtractAndMergeByCode(
                    List.of(cp), Set.of(PROSPECTIVE)
            );

            assertThat(result.get(PROSPECTIVE).periods()).containsExactly(
                    p("2020-01-01", "2025-12-31")
            );
        }

        @Test
        void denyPunchesHoleInNonRetroPermit() {
            Provision prospectivePermit = new Provision(PROSPECTIVE, p("2020-01-01", "2025-12-31"), true);
            Provision prospectiveDeny = new Provision(PROSPECTIVE, p("2022-01-01", "2022-12-31"), false);
            Provision retroPermit = new Provision(RETRO, p("2030-01-01", "2035-12-31"), true);
            ConsentProvisions cp = new ConsentProvisions("patient1", null,
                    List.of(prospectivePermit, prospectiveDeny, retroPermit));

            Map<TermCode, NonContinuousPeriod> result = calculator.subtractAndMergeByCode(
                    List.of(cp), Set.of(PROSPECTIVE)
            );

            assertThat(result.get(PROSPECTIVE).periods()).containsExactly(
                    p("2020-01-01", "2021-12-31"),
                    p("2023-01-01", "2025-12-31")
            );
        }
    }

    @Nested
    class IntersectConsentTests {
        @BeforeEach
        void setUp() {
            calculator = new ConsentCalculator(noRetroConfig());
        }

        @Test
        void intersectConsent_multipleCodes_overlap() throws ConsentViolatedException {
            Map<TermCode, NonContinuousPeriod> consentsByCode = Map.of(
                    CODE_1, new NonContinuousPeriod(List.of(p("2024-01-01", "2024-01-10"))),
                    CODE_2, new NonContinuousPeriod(List.of(p("2024-01-05", "2024-01-15")))
            );

            NonContinuousPeriod intersected = calculator.intersectConsent(consentsByCode);

            assertThat(intersected.periods()).containsExactly(p("2024-01-05", "2024-01-10"));
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
    class GateCodeTests {

        @BeforeEach
        void setUp() {
            calculator = new ConsentCalculator(new ConsentCodeConfig(List.of(
                    new ProspectiveEntry(GATE_CODE, true, List.of(), List.of(), null),
                    new ProspectiveEntry(CODE_1, false, List.of(), List.of(), null)
            )));
        }

        @Test
        void patientWithActiveGateIsIncluded() {
            LocalDate today = LocalDate.now();
            Provision gatePermit = new Provision(GATE_CODE, new Period(today.minusYears(1), today.plusYears(1)), true);
            Provision dataPermit = new Provision(CODE_1, p("2024-01-01", "2024-12-31"), true);
            ConsentProvisions cp = new ConsentProvisions("p1", null, List.of(gatePermit, dataPermit));

            Map<String, NonContinuousPeriod> result = calculator.calculateConsent(
                    Set.of(GATE_CODE, CODE_1),
                    Map.of("p1", List.of(cp))
            );

            assertThat(result).containsKey("p1");
        }

        @Test
        void patientWithExpiredGateIsExcluded() {
            Provision gatePermit = new Provision(GATE_CODE, p("2020-01-01", "2021-12-31"), true);
            Provision dataPermit = new Provision(CODE_1, p("2020-01-01", "2021-12-31"), true);
            ConsentProvisions cp = new ConsentProvisions("p1", null, List.of(gatePermit, dataPermit));

            Map<String, NonContinuousPeriod> result = calculator.calculateConsent(
                    Set.of(GATE_CODE, CODE_1),
                    Map.of("p1", List.of(cp))
            );

            assertThat(result).doesNotContainKey("p1");
        }

        @Test
        void patientWithFutureGateIsExcluded() {
            Provision gatePermit = new Provision(GATE_CODE, p("2030-01-01", "2040-12-31"), true);
            Provision dataPermit = new Provision(CODE_1, p("2030-01-01", "2040-12-31"), true);
            ConsentProvisions cp = new ConsentProvisions("p1", null, List.of(gatePermit, dataPermit));

            Map<String, NonContinuousPeriod> result = calculator.calculateConsent(
                    Set.of(GATE_CODE, CODE_1),
                    Map.of("p1", List.of(cp))
            );

            assertThat(result).doesNotContainKey("p1");
        }

        @Test
        void patientMissingDataCodeIsExcluded() {
            LocalDate today = LocalDate.now();
            Provision gatePermit = new Provision(GATE_CODE, new Period(today.minusYears(1), today.plusYears(1)), true);
            ConsentProvisions cp = new ConsentProvisions("p1", null, List.of(gatePermit));

            Map<String, NonContinuousPeriod> result = calculator.calculateConsent(
                    Set.of(GATE_CODE, CODE_1),
                    Map.of("p1", List.of(cp))
            );

            assertThat(result).doesNotContainKey("p1");
        }
    }

    @Nested
    class CalculateConsentTests {
        @BeforeEach
        void setUp() {
            calculator = new ConsentCalculator(dataCodesConfig());
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

            assertThat(result.get("patient1").periods()).containsExactly(p("2024-01-05", "2024-01-10"));
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

            assertThat(result.get("patient1").periods()).containsExactly(p("2024-01-01", "2024-01-10"));
        }
    }
}
