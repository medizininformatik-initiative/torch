package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.model.crtdl.Code;
import de.medizininformatikinitiative.torch.model.crtdl.Filter;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Questionnaire;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class FilterServiceTest {

    static final String SYS = "some-system-155431";
    static final String CODE_1 = "some-code-155445";
    static final String CODE_2 = "some-code-155501";
    static final String CODE_3 = "some-code-150701";
    static final String CODE_4 = "some-code-151420";
    static final String DISPLAY = "some-display";
    static final String OBSERVATION = "Observation";
    static final String TOKEN = "token";
    static final String CODE = "code";

    static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    static final DateTimeFormatter LOCALDATE_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM-dd");
    public static final String DATE = "date";

    static FilterService filterService = new FilterService(FhirContext.forR4(), "search-parameters.json");

    @BeforeAll
    static void setUp() {
        filterService.init();
    }

    @Nested
    class MultipleFilters {
        @Test
        public void test_compile_onlyFirstFilterSatisfied() {
            var observation = new Observation().setCode(new CodeableConcept().setCoding(List.of(
                    new Coding(SYS, CODE_1, DISPLAY))));

            var result = filterService.compileFilter(List.of(
                            new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_1))),
                            new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_2)))), OBSERVATION)
                    .test(observation);

            assertTrue(result);
        }

        @Test
        public void test_compile_onlySecondFilterSatisfied() {
            var observation = new Observation().setCode(new CodeableConcept().setCoding(List.of(
                    new Coding(SYS, CODE_2, DISPLAY))));

            var result = filterService.compileFilter(List.of(
                            new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_1))),
                            new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_2)))), OBSERVATION)
                    .test(observation);

            assertTrue(result);
        }
    }

    @Test
    public void test_compile_MultipleResources() {
        var observation_1 = new Observation().setCode(new CodeableConcept().setCoding(List.of(
                new Coding(SYS, CODE_1, DISPLAY))));
        var observation_2 = new Observation().setCode(new CodeableConcept().setCoding(List.of(
                new Coding(SYS, CODE_1, DISPLAY))));
        var observation_3 = new Observation().setCode(new CodeableConcept().setCoding(List.of(
                new Coding(SYS, CODE_2, DISPLAY))));

        var compiledFilter = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_1)))), OBSERVATION);
        var result_1 = compiledFilter.test(observation_1);
        var result_2 = compiledFilter.test(observation_2);
        var result_3 = compiledFilter.test(observation_3);

        assertTrue(result_1);
        assertTrue(result_2);
        assertFalse(result_3);
    }

    @Nested
    class TestCodeableConcept {

        public static final String ALLERGY_INTOLERANCE = "AllergyIntolerance";

        @Test
        public void test_compile_singleFilterCode_singleResourceCode() {
            var observation = new Observation().setCode(new CodeableConcept().setCoding(List.of(
                    new Coding(SYS, CODE_1, DISPLAY))));

            var result_sameCode = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_1)))), OBSERVATION)
                    .test(observation);
            var result_differentCode = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_2)))), OBSERVATION)
                    .test(observation);

            assertTrue(result_sameCode);
            assertFalse(result_differentCode);
        }

        @Test
        public void test_compile_singleFilterCode_multipleResourceCodes() {
            var observation = new Observation().setCode(new CodeableConcept().setCoding(List.of(
                    new Coding(SYS, CODE_1, DISPLAY),
                    new Coding(SYS, CODE_2, DISPLAY))));

            var result_matchFirstCode = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_1)))), OBSERVATION)
                    .test(observation);
            var result_matchSecondCode = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_2)))), OBSERVATION)
                    .test(observation);
            var result_differentCode = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_3)))), OBSERVATION)
                    .test(observation);

            assertTrue(result_matchFirstCode);
            assertTrue(result_matchSecondCode);
            assertFalse(result_differentCode);
        }

        @Test
        public void test_compile_multipleFilterCodes_singleResourceCode() {
            var observation = new Observation().setCode(new CodeableConcept().setCoding(List.of(
                    new Coding(SYS, CODE_1, DISPLAY))));


            var result_matchFirstCode = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_1), new Code(SYS, CODE_2)))), OBSERVATION)
                    .test(observation);
            var result_matchSecondCode = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_2), new Code(SYS, CODE_1)))), OBSERVATION)
                    .test(observation);
            var result_differentCodes = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_3), new Code(SYS, CODE_4)))), OBSERVATION)
                    .test(observation);

            assertTrue(result_matchFirstCode);
            assertTrue(result_matchSecondCode);
            assertFalse(result_differentCodes);
        }

        @Test
        public void test_compile_multipleFilterCodes_multipleResourceCodes() {
            var observation = new Observation().setCode(new CodeableConcept().setCoding(List.of(
                    new Coding(SYS, CODE_1, DISPLAY),
                    new Coding(SYS, CODE_2, DISPLAY))));


            var result_firstMatchesFirst = filterService.compileFilter(List.of(
                    new Filter(TOKEN, CODE, List.of(
                            new Code(SYS, CODE_1), 
                            new Code(SYS, CODE_3)))), OBSERVATION)
                    .test(observation);
            var result_firstMatchesSecond = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_3), new Code(SYS, CODE_1)))), OBSERVATION)
                    .test(observation);
            var result_secondMatchesFirst = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_2), new Code(SYS, CODE_3)))), OBSERVATION)
                    .test(observation);
            var result_secondMatchesSecond = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_3), new Code(SYS, CODE_2)))), OBSERVATION)
                    .test(observation);
            var result_bothMatchBoth_sameOrder = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_1), new Code(SYS, CODE_2)))), OBSERVATION)
                    .test(observation);
            var result_bothMatchBoth_differentOrder = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_2), new Code(SYS, CODE_1)))), OBSERVATION)
                    .test(observation);
            var result_noMatch = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_3), new Code(SYS, CODE_4)))), OBSERVATION)
                    .test(observation);

            assertTrue(result_firstMatchesFirst);
            assertTrue(result_firstMatchesSecond);
            assertTrue(result_secondMatchesFirst);
            assertTrue(result_secondMatchesSecond);
            assertTrue(result_bothMatchBoth_sameOrder);
            assertTrue(result_bothMatchBoth_differentOrder);
            assertFalse(result_noMatch);
        }

        @Test
        public void test_compile_multipleCodeableConcepts() {
            var observation = new AllergyIntolerance()
                    .setCode(new CodeableConcept().setCoding(List.of(new Coding(SYS, CODE_1, DISPLAY))))
                    .setReaction(List.of(new AllergyIntolerance.AllergyIntoleranceReactionComponent().setSubstance(
                            new CodeableConcept().setCoding(List.of(new Coding(SYS, CODE_2, DISPLAY))))));

            var result_matchFirst = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_1)))), ALLERGY_INTOLERANCE)
                    .test(observation);
            var result_matchSecond = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_2)))), ALLERGY_INTOLERANCE)
                    .test(observation);

            assertTrue(result_matchFirst);
            assertTrue(result_matchSecond);
        }

        @Test
        public void test_compile_missingCodeableConcept() {
            var observation = new Observation();

            var result = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_1)))), OBSERVATION)
                    .test(observation);

            assertFalse(result);
        }
    }

    @Nested
    class TestCoding {

        public static final String QUESTIONNAIRE = "Questionnaire";

        @Test
        public void test_compile_singleCoding() {
            var resource = new Questionnaire().setItem(List.of(new Questionnaire.QuestionnaireItemComponent().setCode(List.of(new Coding(SYS, CODE_1, DISPLAY)))));

            var result_sameCode = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_1)))), QUESTIONNAIRE)
                    .test(resource);

            assertTrue(result_sameCode);
        }

        @Test
        public void test_compile_multipleCodings() {
            var resource = new Questionnaire().setItem(List.of(new Questionnaire.QuestionnaireItemComponent().setCode(List.of(
                    new Coding(SYS, CODE_1, DISPLAY),
                    new Coding(SYS, CODE_2, DISPLAY)))));

            var result_matchFirst = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_1)))), QUESTIONNAIRE)
                    .test(resource);
            var result_matchSecond = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_2)))), QUESTIONNAIRE)
                    .test(resource);

            assertTrue(result_matchFirst);
            assertTrue(result_matchSecond);
        }

        @Test
        public void test_compile_codingMissing() {
            var resource = new Questionnaire();

            var result = filterService.compileFilter(List.of(new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_1)))), OBSERVATION)
                    .test(resource);

            assertFalse(result);
        }
    }
    
    @Nested
    class TestDate {

        @Nested
        class FilterDate {

            @Nested
            class OnlyFilterStart {

                @Nested
                class ResourceSingleDate {
                    @Test
                    public void test_compile_resourceDateIsBeforeFilter() throws ParseException {
                        var resourceDate = DATE_FORMAT.parse("2024-10-14");
                        var filterStartDate = LocalDate.parse("2024-10-15", LOCALDATE_FORMAT);
                        var resource = new Observation().setEffective(new DateTimeType(resourceDate));

                        var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, null)), OBSERVATION)
                                .test(resource);

                        assertFalse(result);
                    }

                    @Test
                    public void test_compile_resourceDateIsAfterFilter() throws ParseException {
                        var resourceDate = DATE_FORMAT.parse("2024-10-16");
                        var filterStartDate = LocalDate.parse("2024-10-15", LOCALDATE_FORMAT);
                        var resource = new Observation().setEffective(new DateTimeType(resourceDate));

                        var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, null)), OBSERVATION)
                                .test(resource);

                        assertTrue(result);
                    }

                    @Test
                    public void test_compile_resourceDateIsEqualFilter() throws ParseException {
                        var resourceDate = DATE_FORMAT.parse("2024-10-16");
                        var filterStartDate = LocalDate.parse("2024-10-16", LOCALDATE_FORMAT);
                        var resource = new Observation().setEffective(new DateTimeType(resourceDate));

                        var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, null)), OBSERVATION)
                                .test(resource);

                        assertTrue(result);
                    }
                }

                @Nested
                class ResourcePeriod {
                    @Test
                    public void test_compile_resourceRangeIsBeforeFilter() throws ParseException {
                        var resourceStart = DATE_FORMAT.parse("2024-10-15");
                        var resourceEnd = DATE_FORMAT.parse("2024-10-16");
                        var filterStartDate = LocalDate.parse("2024-10-17", LOCALDATE_FORMAT);
                        var resource = new Observation().setEffective(new Period().setStart(resourceStart).setEnd(resourceEnd));

                        var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, null)), OBSERVATION)
                                .test(resource);

                        assertFalse(result);
                    }

                    @Test
                    public void test_compile_resourceRangeIsAfterFilter() throws ParseException {
                        var resourceStart = DATE_FORMAT.parse("2024-10-15");
                        var resourceEnd = DATE_FORMAT.parse("2024-10-16");
                        var filterStartDate = LocalDate.parse("2024-10-14", LOCALDATE_FORMAT);
                        var resource = new Observation().setEffective(new Period().setStart(resourceStart).setEnd(resourceEnd));

                        var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, null)), OBSERVATION)
                                .test(resource);

                        assertTrue(result);
                    }

                    @Test
                    public void test_compile_resourceRangeIsEqualFilter() throws ParseException {
                        var resourceStart = DATE_FORMAT.parse("2024-10-15");
                        var resourceEnd = DATE_FORMAT.parse("2024-10-16");
                        var filterStartDate = LocalDate.parse("2024-10-15", LOCALDATE_FORMAT);
                        var resource = new Observation().setEffective(new Period().setStart(resourceStart).setEnd(resourceEnd));

                        var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, null)), OBSERVATION)
                                .test(resource);

                        assertTrue(result);
                    }
                }

            }

            @Nested
            class OnlyFilterEnd {

                @Nested
                class ResourceSingleDate {
                    @Test
                    public void test_compile_resourceDateIsBeforeFilter() throws ParseException {
                        var resourceDate = DATE_FORMAT.parse("2024-10-14");
                        var filterEndDate = LocalDate.parse("2024-10-15", LOCALDATE_FORMAT);
                        var resource = new Observation().setEffective(new DateTimeType(resourceDate));

                        var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, null, filterEndDate)), OBSERVATION)
                                .test(resource);

                        assertTrue(result);
                    }

                    @Test
                    public void test_compile_resourceDateIsAfterFilter() throws ParseException {
                        var resourceDate = DATE_FORMAT.parse("2024-10-16");
                        var filterEndDate = LocalDate.parse("2024-10-15", LOCALDATE_FORMAT);
                        var resource = new Observation().setEffective(new DateTimeType(resourceDate));

                        var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, null, filterEndDate)), OBSERVATION)
                                .test(resource);

                        assertFalse(result);
                    }

                    @Test
                    public void test_compile_resourceDateIsEqualFilter() throws ParseException {
                        var resourceDate = DATE_FORMAT.parse("2024-10-16");
                        var filterEndDate = LocalDate.parse("2024-10-16", LOCALDATE_FORMAT);
                        var resource = new Observation().setEffective(new DateTimeType(resourceDate));

                        var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, null, filterEndDate)), OBSERVATION)
                                .test(resource);

                        assertTrue(result);
                    }
                }

                @Nested
                class ResourcePeriod {
                    @Test
                    public void test_compile_resourceRangeIsBeforeFilter() throws ParseException {
                        var resourceStart = DATE_FORMAT.parse("2024-10-15");
                        var resourceEnd = DATE_FORMAT.parse("2024-10-16");
                        var filterEndDate = LocalDate.parse("2024-10-17", LOCALDATE_FORMAT);
                        var resource = new Observation().setEffective(new Period().setStart(resourceStart).setEnd(resourceEnd));

                        var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, null, filterEndDate)), OBSERVATION)
                                .test(resource);

                        assertTrue(result);
                    }

                    @Test
                    public void test_compile_resourceDateIsAfterFilter() throws ParseException {
                        var resourceStart = DATE_FORMAT.parse("2024-10-15");
                        var resourceEnd = DATE_FORMAT.parse("2024-10-16");
                        var filterEndDate = LocalDate.parse("2024-10-14", LOCALDATE_FORMAT);
                        var resource = new Observation().setEffective(new Period().setStart(resourceStart).setEnd(resourceEnd));

                        var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, null, filterEndDate)), OBSERVATION)
                                .test(resource);

                        assertFalse(result);
                    }

                    @Test
                    public void test_compile_resourceDateIsEqualFilter() throws ParseException {
                        var resourceStart = DATE_FORMAT.parse("2024-10-15");
                        var resourceEnd = DATE_FORMAT.parse("2024-10-16");
                        var filterEndDate = LocalDate.parse("2024-10-16", LOCALDATE_FORMAT);
                        var resource = new Observation().setEffective(new Period().setStart(resourceStart).setEnd(resourceEnd));

                        var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, null, filterEndDate)), OBSERVATION)
                                .test(resource);

                        assertTrue(result);
                    }
                }
            }
        }

        @Nested
        class FilterRange {

            @Test
            public void test_compile_resourceDateIsBeforeFilter() throws ParseException {
                var resourceDate = DATE_FORMAT.parse("2024-10-14");
                var filterStartDate = LocalDate.parse("2024-10-15", LOCALDATE_FORMAT);
                var filterEndDate = LocalDate.parse("2024-10-18", LOCALDATE_FORMAT);
                var resource = new Observation().setEffective(new DateTimeType(resourceDate));

                var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, filterEndDate)), OBSERVATION)
                        .test(resource);

                assertFalse(result);
            }

            @Test
            public void test_compile_resourceDateIsInsideFilter() throws ParseException {
                var resourceDate = DATE_FORMAT.parse("2024-10-16");
                var filterStartDate = LocalDate.parse("2024-10-15", LOCALDATE_FORMAT);
                var filterEndDate = LocalDate.parse("2024-10-18", LOCALDATE_FORMAT);
                var resource = new Observation().setEffective(new DateTimeType(resourceDate));

                var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, filterEndDate)), OBSERVATION)
                        .test(resource);

                assertTrue(result);
            }

            @Test
                public void test_compile_resourceDateIsAfterFilter() throws ParseException {
                var resourceDate = DATE_FORMAT.parse("2024-10-19");
                var filterStartDate = LocalDate.parse("2024-10-15", LOCALDATE_FORMAT);
                var filterEndDate = LocalDate.parse("2024-10-18", LOCALDATE_FORMAT);
                var resource = new Observation().setEffective(new DateTimeType(resourceDate));

                var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, filterEndDate)), OBSERVATION)
                        .test(resource);

                assertFalse(result);
            }

            @Test
            public void test_compile_resourcePeriodIBeforeFilter() throws ParseException {
                var resourceStart = DATE_FORMAT.parse("2024-10-15");
                var resourceEnd = DATE_FORMAT.parse("2024-10-16");
                var filterStartDate = LocalDate.parse("2024-10-17", LOCALDATE_FORMAT);
                var filterEndDate = LocalDate.parse("2024-10-18", LOCALDATE_FORMAT);
                var resource = new Observation().setEffective(new Period().setStart(resourceStart).setEnd(resourceEnd));

                var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, filterEndDate)), OBSERVATION)
                        .test(resource);

                assertFalse(result);
            }

            @Test
            public void test_compile_resourcePeriodIsAfterFilter() throws ParseException {
                var resourceStart = DATE_FORMAT.parse("2024-10-19");
                var resourceEnd = DATE_FORMAT.parse("2024-10-20");
                var filterStartDate = LocalDate.parse("2024-10-17", LOCALDATE_FORMAT);
                var filterEndDate = LocalDate.parse("2024-10-18", LOCALDATE_FORMAT);
                var resource = new Observation().setEffective(new Period().setStart(resourceStart).setEnd(resourceEnd));

                var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, filterEndDate)), OBSERVATION)
                        .test(resource);

                assertFalse(result);
            }

            @Test
            public void test_compile_resourcePeriodStartEqualsFilterEnd() throws ParseException {
                var resourceStart = DATE_FORMAT.parse("2024-10-19");
                var resourceEnd = DATE_FORMAT.parse("2024-10-20");
                var filterStartDate = LocalDate.parse("2024-10-17", LOCALDATE_FORMAT);
                var filterEndDate = LocalDate.parse("2024-10-19", LOCALDATE_FORMAT);
                var resource = new Observation().setEffective(new Period().setStart(resourceStart).setEnd(resourceEnd));

                var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, filterEndDate)), OBSERVATION)
                        .test(resource);

                assertTrue(result);
            }

            @Test
            public void test_compile_resourcePeriodStartInFilter() throws ParseException {
                var resourceStart = DATE_FORMAT.parse("2024-10-18");
                var resourceEnd = DATE_FORMAT.parse("2024-10-20");
                var filterStartDate = LocalDate.parse("2024-10-17", LOCALDATE_FORMAT);
                var filterEndDate = LocalDate.parse("2024-10-19", LOCALDATE_FORMAT);
                var resource = new Observation().setEffective(new Period().setStart(resourceStart).setEnd(resourceEnd));

                var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, filterEndDate)), OBSERVATION)
                        .test(resource);

                assertTrue(result);
            }

            @Test
            public void test_compile_resourcePeriodStartEqualsFilterStart() throws ParseException {
                var resourceStart = DATE_FORMAT.parse("2024-10-17");
                var resourceEnd = DATE_FORMAT.parse("2024-10-20");
                var filterStartDate = LocalDate.parse("2024-10-17", LOCALDATE_FORMAT);
                var filterEndDate = LocalDate.parse("2024-10-19", LOCALDATE_FORMAT);
                var resource = new Observation().setEffective(new Period().setStart(resourceStart).setEnd(resourceEnd));

                var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, filterEndDate)), OBSERVATION)
                        .test(resource);

                assertTrue(result);
            }

            @Test
            public void test_compile_resourcePeriodEndEqualsFilterEnd() throws ParseException {
                var resourceStart = DATE_FORMAT.parse("2024-10-16");
                var resourceEnd = DATE_FORMAT.parse("2024-10-19");
                var filterStartDate = LocalDate.parse("2024-10-17", LOCALDATE_FORMAT);
                var filterEndDate = LocalDate.parse("2024-10-19", LOCALDATE_FORMAT);
                var resource = new Observation().setEffective(new Period().setStart(resourceStart).setEnd(resourceEnd));

                var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, filterEndDate)), OBSERVATION)
                        .test(resource);

                assertTrue(result);
            }

            @Test
            public void test_compile_resourcePeriodEndInFilter() throws ParseException {
                var resourceStart = DATE_FORMAT.parse("2024-10-16");
                var resourceEnd = DATE_FORMAT.parse("2024-10-18");
                var filterStartDate = LocalDate.parse("2024-10-17", LOCALDATE_FORMAT);
                var filterEndDate = LocalDate.parse("2024-10-19", LOCALDATE_FORMAT);
                var resource = new Observation().setEffective(new Period().setStart(resourceStart).setEnd(resourceEnd));

                var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, filterEndDate)), OBSERVATION)
                        .test(resource);

                assertTrue(result);
            }

            @Test
            public void test_compile_resourcePeriodEndEqualsFilterStart() throws ParseException {
                var resourceStart = DATE_FORMAT.parse("2024-10-16");
                var resourceEnd = DATE_FORMAT.parse("2024-10-17");
                var filterStartDate = LocalDate.parse("2024-10-17", LOCALDATE_FORMAT);
                var filterEndDate = LocalDate.parse("2024-10-19", LOCALDATE_FORMAT);
                var resource = new Observation().setEffective(new Period().setStart(resourceStart).setEnd(resourceEnd));

                var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, filterEndDate)), OBSERVATION)
                        .test(resource);

                assertTrue(result);
            }

            @Test
            public void test_compile_resourcePeriodFullyOverlapsAndExceedsFilter() throws ParseException {
                var resourceStart = DATE_FORMAT.parse("2024-10-16");
                var resourceEnd = DATE_FORMAT.parse("2024-10-20");
                var filterStartDate = LocalDate.parse("2024-10-17", LOCALDATE_FORMAT);
                var filterEndDate = LocalDate.parse("2024-10-19", LOCALDATE_FORMAT);
                var resource = new Observation().setEffective(new Period().setStart(resourceStart).setEnd(resourceEnd));

                var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, filterEndDate)), OBSERVATION)
                        .test(resource);

                assertTrue(result);
            }

            @Test
            public void test_compile_resourceDateMissing() {
                var filterStartDate = LocalDate.parse("2024-10-17", LOCALDATE_FORMAT);
                var filterEndDate = LocalDate.parse("2024-10-19", LOCALDATE_FORMAT);
                var resource = new Observation();

                var result = filterService.compileFilter(List.of(new Filter(DATE, DATE, filterStartDate, filterEndDate)), OBSERVATION)
                        .test(resource);

                assertFalse(result);
            }
        }

    }

    @Nested
    class TestDateCodeableConceptMix {
        @Test
        public void test_compile_onlyDateFilterSatisfied() throws ParseException {
            var resourceDate = DATE_FORMAT.parse("2024-10-16");
            var filterStartDate = LocalDate.parse("2024-10-15", LOCALDATE_FORMAT);
            var filterEndDate = LocalDate.parse("2024-10-18", LOCALDATE_FORMAT);
            var resource = new Observation().setEffective(new DateTimeType(resourceDate));


            var result = filterService.compileFilter(List.of(
                            new Filter(DATE, DATE, filterStartDate, filterEndDate),
                            new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_1)))), OBSERVATION)
                    .test(resource);

            assertTrue(result);
        }

        @Test
        public void test_compile_onlyCodeFilterSatisfied() {
            var filterStartDate = LocalDate.parse("2024-10-15", LOCALDATE_FORMAT);
            var filterEndDate = LocalDate.parse("2024-10-18", LOCALDATE_FORMAT);
            var resource = new Observation().setCode(new CodeableConcept().setCoding(List.of(
                    new Coding(SYS, CODE_1, DISPLAY))));

            var result = filterService.compileFilter(List.of(
                            new Filter(DATE, DATE, filterStartDate, filterEndDate),
                            new Filter(TOKEN, CODE, List.of(new Code(SYS, CODE_1)))), OBSERVATION)
                    .test(resource);

            assertTrue(result);
        }
    }

}
