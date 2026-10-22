package de.medizininformatikinitiative.torch.model.sq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.model.mapping.*;
import de.medizininformatikinitiative.torch.model.sq.expanded.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static de.medizininformatikinitiative.torch.model.sq.Comparator.*;
import static de.medizininformatikinitiative.torch.model.sq.TestUtil.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CriterionTest {

    static final String COMPONENT_CODE_VALUE_QUANTITY = "component-code-value-quantity";
    static final TermCode VERIFICATION_STATUS = TermCode.of("http://hl7.org", "verification-status",
            "Verification Status");
    static final TermCode CONFIRMED = TermCode.of("http://terminology.hl7.org/CodeSystem/condition-ver-status",
            "confirmed", "Confirmed");
    static final TermCode UNCONFIRMED = TermCode.of("http://terminology.hl7.org/CodeSystem/condition-ver-status",
            "unconfirmed", "Unconfirmed");
    static final TermCode POSITIVE = TermCode.of("http://snomed.info/sct", "positive", "positive");
    static final TermCode MALE = TermCode.of("http://hl7.org/fhir/administrative-gender", "male", "Male");
    static final TermCode FEMALE = TermCode.of("http://hl7.org/fhir/administrative-gender", "female", "Female");
    static final TermCode OBSERVATION_STATUS = TermCode.of("http://hl7.org/fhir", "observation-status", "Observation Status");
    static final TermCode FINAL = TermCode.of("http://hl7.org/fhir/observation-status", "final", "Final");
    static final TermCode AGE = TermCode.of("http://snomed.info/sct", "424144002", "Gegenwärtiges chronologisches Alter");
    static final TermCode YEAR_UNIT = new TermCode("someSystem", "a", "a");
    static final TermCode WEEK_UNIT = new TermCode("someSystem", "wk", "mo");
    static final TermCode MONTH_UNIT = new TermCode("someSystem", "mo", "mo");
    static final BigDecimal AGE_OF_5 = BigDecimal.valueOf(5);
    static final BigDecimal DECIMAL = BigDecimal.valueOf(5);
    static final TermCode GRAM_PER_DECILITER = new TermCode("http://unitsofmeasure.org", "g/dL", "g/dL");
    static final TermCode COMPOSITE_CODE = new TermCode("http://loing.org", "8480-6", "Sistolic Bloodpressure");
    static final TermCode BLOOD_PRESSURE = new TermCode("http://loing.org", "8480-6", "Systolischer Blutdruck");
    static final TermCode TERM_CODE = TermCode.of("system", "code", "display");
    static final ContextualTermCode CONTEXTUAL_TERM_CODE = ContextualTermCode.of(CONTEXT, TERM_CODE);
    static final ContextualConcept CONTEXTUAL_CONCEPT = ContextualConcept.of(CONTEXT, Concept.of(TERM_CODE));

    @Mock
    MappingContext mappingContext;

    @Nested
    @DisplayName("from JSON")
    class FromJson {

        @Test
        @DisplayName("missing context")
        void missingContext() throws Exception {
            assertThatThrownBy(() -> parse("{}")).hasMessage("Cannot construct instance of `de.medizininformatikinitiative.torch.model.sq.Criterion`, problem: missing JSON property: context\n at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 2]");
        }

        @Test
        @DisplayName("concept only")
        void conceptOnly() throws Exception {
            var criterion = parse("""
                    {
                      "context": {
                        "system": "context-system",
                        "code": "context-code",
                        "display": "context-display"
                      },
                      "termCodes": [
                        {
                          "system": "system",
                          "code": "code",
                          "display": "display"
                        }
                      ]
                    }
                    """);

            assertThat(criterion).isEqualTo(Criterion.of(CONTEXTUAL_CONCEPT));
        }

        @Test
        @DisplayName("with time restriction")
        void withTimeRestriction() throws Exception {
            var criterion = parse("""
                    {
                      "context": {
                        "system": "context-system",
                        "code": "context-code",
                        "display": "context-display"
                      },
                      "termCodes": [
                        {
                          "system": "system",
                          "code": "code",
                          "display": "display"
                        }
                      ],
                      "timeRestriction": {
                        "afterDate": "2021-09-09",
                        "beforeDate": "2021-10-09"
                      }
                    }
                    """);

            assertThat(criterion).isEqualTo(Criterion.of(CONTEXTUAL_CONCEPT)
                    .appendTimeRestrictionFilter(new TimeRestriction.Interval(LocalDate.of(2021, 9, 9),
                            LocalDate.of(2021, 10, 9))));
        }

        @Test
        @DisplayName("with reference attribute filter")
        void withReferenceAttributeFilter() throws Exception {
            var criterion = parse("""
                    {
                      "context": {
                        "system": "context-system",
                        "code": "context-code",
                        "display": "context-display"
                      },
                      "termCodes": [
                        {
                          "system": "system",
                          "code": "code",
                          "display": "display"
                        }
                      ],
                      "attributeFilters": [
                        {
                          "type": "reference",
                          "attributeCode": {
                            "system": "attr-code-system",
                            "code": "attr-code-code",
                            "display": "attr-code-display"
                          },
                          "criteria": [
                            {
                              "context": {
                                "system": "ref-crit-context-system",
                                "code": "ref-crit-context-code",
                                "display": "ref-crit-context-display"
                              },
                              "termCodes": [
                                {
                                  "system": "ref-crit-system",
                                  "code": "ref-crit-code",
                                  "display": "ref-crit-display"
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                    """);

            var attrCode = TermCode.of("attr-code-system", "attr-code-code", "attr-code-display");
            var refCritConcept = ContextualConcept.of(TermCode.of("ref-crit-context-system", "ref-crit-context-code", "ref-crit-context-display"),
                    Concept.of(TermCode.of("ref-crit-system", "ref-crit-code", "ref-crit-display")));
            assertThat(criterion).isEqualTo(Criterion.of(CONTEXTUAL_CONCEPT)
                    .appendAttributeFilter(AttributeFilter.ofReference(attrCode, Criterion.of(refCritConcept))));
        }

        static Criterion parse(String s) throws JsonProcessingException {
            return new ObjectMapper().readValue(s, Criterion.class);
        }
    }

    @Nested
    @DisplayName("from JSON node")
    class FromJsonNode {

        @Test
        @DisplayName("missing context")
        void missingContext() throws Exception {
            assertThatThrownBy(() -> parse("{}")).hasMessage("com.fasterxml.jackson.databind.exc.ValueInstantiationException: Cannot construct instance of `de.medizininformatikinitiative.torch.model.sq.Criterion`, problem: missing JSON property: context\n at [Source: UNKNOWN; byte offset: #UNKNOWN]");
        }

        @Test
        @DisplayName("concept only")
        void conceptOnly() throws Exception {
            var criterion = parse("""
                    {
                      "context": {
                        "system": "context-system",
                        "code": "context-code",
                        "display": "context-display"
                      },
                      "termCodes": [
                        {
                          "system": "system",
                          "code": "code",
                          "display": "display"
                        }
                      ]
                    }
                    """);

            assertThat(criterion).isEqualTo(Criterion.of(CONTEXTUAL_CONCEPT));
        }

        @Test
        @DisplayName("with time restriction")
        void withTimeRestriction() throws Exception {
            var criterion = parse("""
                    {
                      "context": {
                        "system": "context-system",
                        "code": "context-code",
                        "display": "context-display"
                      },
                      "termCodes": [
                        {
                          "system": "system",
                          "code": "code",
                          "display": "display"
                        }
                      ],
                      "timeRestriction": {
                        "afterDate": "2021-09-09",
                        "beforeDate": "2021-10-09"
                      }
                    }
                    """);

            assertThat(criterion).isEqualTo(Criterion.of(CONTEXTUAL_CONCEPT)
                    .appendTimeRestrictionFilter(new TimeRestriction.Interval(LocalDate.of(2021, 9, 9),
                            LocalDate.of(2021, 10, 9))));
        }

        static Criterion parse(String s) throws JsonProcessingException {
            return Criterion.fromJsonNode(new ObjectMapper().readTree(s));
        }
    }

    @Nested
    @DisplayName("expand")
    class Expand {

        @Test
        @DisplayName("not expandable")
        void notExpandable() throws Exception {
            when(mappingContext.expandConcept(CONTEXTUAL_CONCEPT)).thenThrow(
                    new ContextualConceptNotExpandableException(CONTEXTUAL_CONCEPT));

            assertThatThrownBy(() -> Criterion.of(CONTEXTUAL_CONCEPT).expand(mappingContext))
                .isInstanceOf(ContextualConceptNotExpandableException.class);
        }

        @Test
        @DisplayName("mapping not found")
        void mappingNotFound() throws Exception {
            when(mappingContext.expandConcept(CONTEXTUAL_CONCEPT)).thenReturn(List.of(CONTEXTUAL_TERM_CODE));
            when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenThrow(new MappingNotFoundException(CONTEXTUAL_TERM_CODE));

            assertThatThrownBy(() -> Criterion.of(CONTEXTUAL_CONCEPT).expand(mappingContext))
                .isInstanceOf(MappingNotFoundException.class);
        }

        @Test
        @DisplayName("up to the expansion limit")
        void upToExpansionLimit() throws Exception {
            when(mappingContext.expandConcept(CONTEXTUAL_CONCEPT)).thenReturn(contextualTermCodes(Criterion.MAX_EXPANDED_CRITERIA));
            when(mappingContext.findMapping(any())).thenReturn(Mapping.of(CONTEXTUAL_TERM_CODE, "Condition", "code"));

            assertThat(Criterion.of(CONTEXTUAL_CONCEPT).expand(mappingContext)).hasSize(Criterion.MAX_EXPANDED_CRITERIA);
        }

        @Test
        @DisplayName("over the expansion limit")
        void overExpansionLimit() throws Exception {
            when(mappingContext.expandConcept(CONTEXTUAL_CONCEPT)).thenReturn(contextualTermCodes(Criterion.MAX_EXPANDED_CRITERIA + 1));
            when(mappingContext.findMapping(any())).thenReturn(Mapping.of(CONTEXTUAL_TERM_CODE, "Condition", "code"));

            assertThatThrownBy(() -> Criterion.of(CONTEXTUAL_CONCEPT).expand(mappingContext))
                .isInstanceOf(ExpansionLimitExceededException.class)
                .hasMessageContaining(String.valueOf(Criterion.MAX_EXPANDED_CRITERIA));
        }

        static List<ContextualTermCode> contextualTermCodes(int count) {
            return IntStream.range(0, count)
                    .mapToObj(i -> ContextualTermCode.of(CONTEXT, TermCode.of("system", "code-" + i, "display")))
                    .toList();
        }

        @Nested
        @DisplayName("one concept expansion")
        class OneConceptExpansion {

            static final BigDecimal DECIMAL_1 = BigDecimal.valueOf(163017);
            static final BigDecimal DECIMAL_2 = BigDecimal.valueOf(163019);

            Mapping mapping = Mapping.of(CONTEXTUAL_TERM_CODE, "Condition", "code");
            ExpandedCriterion expandedCriterion = ExpandedCriterion.of("Condition", "code", TERM_CODE);

            static Stream<LocalDate> localDates() {
                return Stream.of(1973, 2000, 2023)
                        .flatMap(year -> Stream.of(1, 4, 12)
                                .flatMap(month -> Stream.of(1, 12, 23)
                                        .map(day -> LocalDate.of(year, month, 5))));
            }

            static Stream<Arguments> localDateIntervals() {
                return localDates().limit(5).flatMap(date1 -> localDates()
                        .filter(date1::isBefore)
                        .limit(5)
                        .map(date2 -> Arguments.of(date1, date2)));
            }

            @BeforeEach
            void setUp() throws Exception {
                when(mappingContext.expandConcept(CONTEXTUAL_CONCEPT)).thenReturn(List.of(CONTEXTUAL_TERM_CODE));
            }

            @Test
            @DisplayName("value mapping not found")
            void valueMappingNotFound() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping);

                assertThatThrownBy(() -> Criterion.of(CONTEXTUAL_CONCEPT, ValueFilter.ofConcept(POSITIVE))
                        .expand(mappingContext))
                .isInstanceOf(ValueMappingNotFoundException.class);
            }

            @Test
            @DisplayName("attribute mapping not found")
            void attributeMappingNotFound() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping);

                assertThatThrownBy(() -> Criterion.of(CONTEXTUAL_CONCEPT).appendAttributeFilter(AttributeFilter.ofConcept(OBSERVATION_STATUS, FINAL))
                        .expand(mappingContext))
                .isInstanceOf(AttributeMappingNotFoundException.class);
            }

            @Test
            @DisplayName("concept only")
            void conceptOnly() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping);

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT).expand(mappingContext);

                assertThat(criteria).containsExactly(expandedCriterion);
            }

            @Test
            @DisplayName("one value filter with one concept")
            void oneValueFilter_OneConcept() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping
                        .withValueFilterMapping(FilterMappingType.CONCEPT, "value-concept"));

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT, ValueFilter.ofConcept(POSITIVE))
                        .expand(mappingContext);

                assertThat(criteria).containsExactly(expandedCriterion
                        .appendFilter(new ExpandedConceptFilter("value-concept", POSITIVE)));
            }

            @Test
            @DisplayName("one value filter with one concept and one attribute filter with one concept")
            void oneValueFilter_OneConcept_OneAttributeFilter_OneConcept() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping
                        .withValueFilterMapping(FilterMappingType.CONCEPT, "value-concept")
                        .appendAttributeMapping(AttributeMapping.code(OBSERVATION_STATUS, "status")));

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT, ValueFilter.ofConcept(POSITIVE))
                        .appendAttributeFilter(AttributeFilter.ofConcept(OBSERVATION_STATUS, FINAL))
                        .expand(mappingContext);

                assertThat(criteria).containsExactly(expandedCriterion
                        .appendFilter(new ExpandedConceptFilter("value-concept", POSITIVE))
                        .appendFilter(new ExpandedCodeFilter("status", "final")));
            }

            @Test
            @DisplayName("one value filter with two concepts")
            void oneValueFilter_TwoConcepts() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping
                        .withValueFilterMapping(FilterMappingType.CONCEPT, "value-concept"));

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT, ValueFilter.ofConcept(MALE, FEMALE))
                        .expand(mappingContext);

                assertThat(criteria).containsExactly(
                        expandedCriterion.appendFilter(new ExpandedConceptFilter("value-concept", MALE)),
                        expandedCriterion.appendFilter(new ExpandedConceptFilter("value-concept", FEMALE)));
            }

            @Test
            @DisplayName("one value filter with two concepts and one fixed criterion")
            void oneValueFilter_TwoConcepts_OneFixedCriteria_OneConcept() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping
                        .withValueFilterMapping(FilterMappingType.CONCEPT, "value-concept")
                        .withFixedCriteria(new FixedCriterion(FixedCriterionType.CODE, "status", List.of(FINAL), null)));

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT, ValueFilter.ofConcept(MALE, FEMALE)).expand(mappingContext);

                assertThat(criteria).containsExactly(
                        expandedCriterion
                                .appendFilter(new ExpandedCodeFilter("status", "final"))
                                .appendFilter(new ExpandedConceptFilter("value-concept", MALE)),
                        expandedCriterion
                                .appendFilter(new ExpandedCodeFilter("status", "final"))
                                .appendFilter(new ExpandedConceptFilter("value-concept", FEMALE)));
            }

            @Test
            @DisplayName("one comparator value filter")
            void oneComparatorValueFilter() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping
                        .withValueFilterMapping(FilterMappingType.CONCEPT, "value-quantity"));

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT, ValueFilter.ofComparator(LESS_THAN,
                        Quantity.of(DECIMAL, GRAM_PER_DECILITER))).expand(mappingContext);

                assertThat(criteria).containsExactly(expandedCriterion
                        .appendFilter(new ExpandedQuantityComparatorFilter("value-quantity", LESS_THAN,
                                Quantity.of(DECIMAL, GRAM_PER_DECILITER))));
            }

            @Test
            @DisplayName("one range value filter")
            void oneRangeValueFilter() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping
                        .withValueFilterMapping(FilterMappingType.CONCEPT, "value-quantity"));

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT, ValueFilter.ofRange(Quantity.of(DECIMAL_1,
                                GRAM_PER_DECILITER), Quantity.of(DECIMAL_2, GRAM_PER_DECILITER)))
                        .expand(mappingContext);

                assertThat(criteria).containsExactly(expandedCriterion
                        .appendFilter(new ExpandedQuantityRangeFilter("value-quantity", Quantity.of(DECIMAL_1,
                                GRAM_PER_DECILITER), Quantity.of(DECIMAL_2, GRAM_PER_DECILITER))));
            }

            @Test
            @DisplayName("one attribute filter with one concept")
            void oneAttributeFilter_OneConcept() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping
                        .appendAttributeMapping(AttributeMapping.coding(VERIFICATION_STATUS, "verification-status")));

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT).appendAttributeFilter(AttributeFilter.ofConcept(
                        VERIFICATION_STATUS, CONFIRMED)).expand(mappingContext);

                assertThat(criteria).containsExactly(expandedCriterion
                        .appendFilter(new ExpandedConceptFilter("verification-status", CONFIRMED)));
            }

            @Test
            @DisplayName("one attribute filter with two concepts")
            void oneAttributeFilter_TwoConcepts() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping
                        .appendAttributeMapping(AttributeMapping.coding(VERIFICATION_STATUS, "verification-status")));

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT).appendAttributeFilter(AttributeFilter.ofConcept(
                        VERIFICATION_STATUS, CONFIRMED, UNCONFIRMED)).expand(mappingContext);

                assertThat(criteria).containsExactly(
                        expandedCriterion.appendFilter(new ExpandedConceptFilter("verification-status", CONFIRMED)),
                        expandedCriterion.appendFilter(new ExpandedConceptFilter("verification-status", UNCONFIRMED)));
            }

            @Test
            @DisplayName("one fixed criterion with one concept")
            void oneFixedCriteria_OneConcept() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping
                        .withFixedCriteria(new FixedCriterion(FixedCriterionType.CODING, "verification-status", List.of(CONFIRMED), null)));

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT).expand(mappingContext);

                assertThat(criteria).containsExactly(expandedCriterion
                        .appendFilter(new ExpandedConceptFilter("verification-status", CONFIRMED)));
            }

            @Test
            @DisplayName("one fixed criterion with two concepts")
            void oneFixedCriteria_TwoConcepts() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping
                        .withFixedCriteria(new FixedCriterion(FixedCriterionType.CODING, "verification-status", List.of(CONFIRMED, UNCONFIRMED), null)));

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT).expand(mappingContext);

                assertThat(criteria).containsExactly(
                        expandedCriterion.appendFilter(new ExpandedConceptFilter("verification-status", CONFIRMED)),
                        expandedCriterion.appendFilter(new ExpandedConceptFilter("verification-status", UNCONFIRMED)));
            }

            @Test
            @DisplayName("one fixed criterion with one concept and a composite code")
            void oneFixedCriterion_OneConcept_withCompositeCode() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping
                        .withFixedCriteria(new FixedCriterion(FixedCriterionType.COMPOSITE_CONCEPT, "verification-status", List.of(CONFIRMED), COMPOSITE_CODE)));

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT).expand(mappingContext);

                assertThat(criteria).containsExactly(expandedCriterion
                        .appendFilter(new ExpandedCompositeConceptFilter("verification-status", COMPOSITE_CODE, CONFIRMED)));
            }

            @Test
            @DisplayName("one composite-comparator filter")
            void oneCompositeComparatorFilter() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping
                        .appendAttributeMapping(AttributeMapping.compositeComparator(BLOOD_PRESSURE,
                                COMPONENT_CODE_VALUE_QUANTITY, COMPOSITE_CODE)));

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT).appendAttributeFilter(new AttributeFilter(
                        BLOOD_PRESSURE, new QuantityComparatorFilterPart(GREATER_THAN, Quantity.of(DECIMAL,
                        GRAM_PER_DECILITER)))).expand(mappingContext);

                assertThat(criteria).containsExactly(expandedCriterion
                        .appendFilter(new ExpandedCompositeQuantityComparatorFilter(COMPONENT_CODE_VALUE_QUANTITY,
                                COMPOSITE_CODE, GREATER_THAN, Quantity.of(DECIMAL, GRAM_PER_DECILITER))));
            }

            @Test
            @DisplayName("one composite-range filter")
            void oneCompositeRangeFilter() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping
                        .appendAttributeMapping(AttributeMapping.compositeRange(BLOOD_PRESSURE,
                                COMPONENT_CODE_VALUE_QUANTITY, COMPOSITE_CODE)));

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT).appendAttributeFilter(new AttributeFilter(
                        BLOOD_PRESSURE, new QuantityRangeFilterPart(Quantity.of(DECIMAL_1, GRAM_PER_DECILITER),
                        Quantity.of(DECIMAL_2, GRAM_PER_DECILITER)))).expand(mappingContext);

                assertThat(criteria).containsExactly(expandedCriterion
                        .appendFilter(new ExpandedCompositeQuantityRangeFilter(COMPONENT_CODE_VALUE_QUANTITY,
                                COMPOSITE_CODE, Quantity.of(DECIMAL_1, GRAM_PER_DECILITER), Quantity.of(DECIMAL_2,
                                GRAM_PER_DECILITER))));
            }

            @Test
            @DisplayName("one composite-concept filter")
            void oneCompositeConceptFilter() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping
                        .appendAttributeMapping(AttributeMapping.compositeConcept(BLOOD_PRESSURE,
                                COMPONENT_CODE_VALUE_QUANTITY, COMPOSITE_CODE)));

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT).appendAttributeFilter(new AttributeFilter(
                        BLOOD_PRESSURE, new ConceptFilterPart(List.of(TERM_CODE)))).expand(mappingContext);

                assertThat(criteria).containsExactly(expandedCriterion
                        .appendFilter(new ExpandedCompositeConceptFilter(COMPONENT_CODE_VALUE_QUANTITY, COMPOSITE_CODE,
                                TERM_CODE)));
            }

            @Test
            @DisplayName("one composite-concept filter with wrong filter type in mapping")
            void oneCompositeConceptFilter_WithWrongFilterType() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping
                        .appendAttributeMapping(AttributeMapping.compositeComparator(BLOOD_PRESSURE,
                                COMPONENT_CODE_VALUE_QUANTITY, COMPOSITE_CODE)));

                assertThatThrownBy(() -> Criterion.of(CONTEXTUAL_CONCEPT).appendAttributeFilter(new AttributeFilter(
                        BLOOD_PRESSURE, new ConceptFilterPart(List.of(TERM_CODE)))).expand(mappingContext))
                .isInstanceOf(ConceptFilterTypeNotExpandableException.class);
            }

            @ParameterizedTest(name = "({0}]")
            @MethodSource("localDates")
            @DisplayName("one time restriction with open start point")
            void oneTimeRestriction_openStart(LocalDate end) throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping
                        .withTimeRestrictionParameter("time-restriction"));

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT)
                        .appendTimeRestrictionFilter(new TimeRestriction.OpenStart(end))
                        .expand(mappingContext);

                assertThat(criteria).containsExactly(expandedCriterion
                        .appendFilter(new ExpandedDateComparatorFilter("time-restriction", LESS_EQUAL, end)));
            }

            @ParameterizedTest(name = "[{0})")
            @MethodSource("localDates")
            @DisplayName("one time restriction with open end point")
            void oneTimeRestriction_openEnd(LocalDate start) throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping
                        .withTimeRestrictionParameter("time-restriction"));

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT)
                        .appendTimeRestrictionFilter(new TimeRestriction.OpenEnd(start))
                        .expand(mappingContext);

                assertThat(criteria).containsExactly(expandedCriterion
                        .appendFilter(new ExpandedDateComparatorFilter("time-restriction", GREATER_EQUAL, start)));
            }

            @ParameterizedTest(name = "[{0}, {1}]")
            @MethodSource("localDateIntervals")
            @DisplayName("one time restriction with start and end point")
            void oneTimeRestriction_openEnd(LocalDate start, LocalDate end) throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE)).thenReturn(mapping
                        .withTimeRestrictionParameter("time-restriction"));

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT)
                        .appendTimeRestrictionFilter(new TimeRestriction.Interval(start, end))
                        .expand(mappingContext);

                assertThat(criteria).containsExactly(expandedCriterion
                        .appendFilter(new ExpandedDateRangeFilter("time-restriction", start, end)));
            }
        }

        @Nested
        @DisplayName("two concept expansions")
        class TwoConceptExpansions {

            static final TermCode TERM_CODE_1 = TermCode.of("system-152138", "code-152142", "display-152148");
            static final TermCode TERM_CODE_2 = TermCode.of("system-160622", "code-160626", "display-160630");
            static final ContextualTermCode CONTEXTUAL_TERM_CODE_1 = ContextualTermCode.of(CONTEXT, TERM_CODE_1);
            static final ContextualTermCode CONTEXTUAL_TERM_CODE_2 = ContextualTermCode.of(CONTEXT, TERM_CODE_2);
            static final ContextualConcept CONTEXTUAL_CONCEPT = ContextualConcept.of(CONTEXT, Concept.of(TERM_CODE));

            Mapping mapping1 = Mapping.of(CONTEXTUAL_TERM_CODE_1, "Condition", "code");
            Mapping mapping2 = Mapping.of(CONTEXTUAL_TERM_CODE_1, "Condition", "code");
            ExpandedCriterion expandedCriterion1 = ExpandedCriterion.of("Condition", "code", TERM_CODE_1);
            ExpandedCriterion expandedCriterion2 = ExpandedCriterion.of("Condition", "code", TERM_CODE_2);

            @BeforeEach
            void setUp() throws Exception {
                when(mappingContext.expandConcept(CONTEXTUAL_CONCEPT)).thenReturn(List.of(
                        CONTEXTUAL_TERM_CODE_1, CONTEXTUAL_TERM_CODE_2));
            }

            @Test
            @DisplayName("concept only")
            void conceptOnly() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE_1)).thenReturn(mapping1);
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE_2)).thenReturn(mapping2);

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT).expand(mappingContext);

                assertThat(criteria).containsExactly(expandedCriterion1,
                        expandedCriterion2);
            }

            @Test
            @DisplayName("one attribute filter with two concepts")
            void oneAttributeFilter_TwoConcepts() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE_1)).thenReturn(mapping1
                        .appendAttributeMapping(AttributeMapping.coding(VERIFICATION_STATUS, "verification-status")));
                when(mappingContext.findMapping(CONTEXTUAL_TERM_CODE_2)).thenReturn(mapping2
                        .appendAttributeMapping(AttributeMapping.coding(VERIFICATION_STATUS, "verification-status")));

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT).appendAttributeFilter(AttributeFilter.ofConcept(
                        VERIFICATION_STATUS, CONFIRMED, UNCONFIRMED)).expand(mappingContext);

                assertThat(criteria).containsExactly(
                        expandedCriterion1.appendFilter(new ExpandedConceptFilter("verification-status", CONFIRMED)),
                        expandedCriterion1.appendFilter(new ExpandedConceptFilter("verification-status", UNCONFIRMED)),
                        expandedCriterion2.appendFilter(new ExpandedConceptFilter("verification-status", CONFIRMED)),
                        expandedCriterion2.appendFilter(new ExpandedConceptFilter("verification-status", UNCONFIRMED)));
            }
        }

        @Nested
        @DisplayName("large mount of concept expansions")
        class LargeAmountOfConceptExpansions {

            static final int NUM_CODES = 1000;
            static final List<TermCode> TERM_CODES = IntStream.range(0, NUM_CODES)
                    .mapToObj(i -> TermCode.of("foo", "%d".formatted(i), "bar"))
                    .toList();
            static final List<ContextualTermCode> CONTEXTUAL_TERM_CODES = TERM_CODES.stream()
                    .map(termCode -> ContextualTermCode.of(CONTEXT, termCode))
                    .toList();

            List<Mapping> mappings = CONTEXTUAL_TERM_CODES.stream()
                    .map(contextualTermCode -> Mapping.of(contextualTermCode, "Condition", "code"))
                    .toList();
            List<ExpandedCriterion> expandedCriteria = TERM_CODES.stream()
                    .map(termCode -> ExpandedCriterion.of("Condition", "code", termCode))
                    .toList();

            @BeforeEach
            void setUp() throws Exception {
                when(mappingContext.expandConcept(CONTEXTUAL_CONCEPT)).thenReturn(CONTEXTUAL_TERM_CODES);
            }

            @Test
            @DisplayName("concept only")
            void conceptOnly() throws Exception {
                for (int i = 0; i < NUM_CODES; i++) {
                    when(mappingContext.findMapping(CONTEXTUAL_TERM_CODES.get(i))).thenReturn(mappings.get(i));
                }

                var criteria = Criterion.of(CONTEXTUAL_CONCEPT).expand(mappingContext);

                assertThat(criteria).isEqualTo(expandedCriteria);
            }
        }

        @Nested
        @DisplayName("with reference filers")
        class ReferenceFilters {

            static final String PARENT_RESOURCE_TYPE = "parent-resource-type";
            static final String CHILD_RESOURCE_TYPE = "child-resource-type";
            static final String PARENT_CODE_SEARCH_PARAM = "parent-code-search-param";
            static final String PARENT_CODE_SEARCH_PARAM_1 = "parent-code-search-param-1";
            static final String PARENT_CODE_SEARCH_PARAM_2 = "parent-code-search-param-2";
            static final String PARENT_REFERENCE_FILTER_SEARCH_PARAM = "parent-reference-filter-search-param";
            static final String PARENT_REFERENCE_FILTER_SEARCH_PARAM_1 = "parent-reference-filter-search-param-1";
            static final String PARENT_REFERENCE_FILTER_SEARCH_PARAM_2 = "parent-reference-filter-search-param-2";
            static final String CHILD_CODE_SEARCH_PARAM = "child-code-search-param";
            static final String CHILD_CODE_SEARCH_PARAM_1 = "child-code-search-param-1";
            static final String CHILD_CODE_SEARCH_PARAM_2 = "child-code-search-param-2";
            static final String CHILD_ATTR_SEARCH_PARAM = "child-attr-search-param";
            static final String CHILD_ATTR_SEARCH_PARAM_1 = "child-attr-search-param-1";
            static final String CHILD_ATTR_SEARCH_PARAM_2 = "child-attr-search-param-2";
            static final TermCode PARENT_TERM_CODE = new TermCode("parent", "code", "display");
            static final TermCode PARENT_TERM_CODE_1 = new TermCode("parent", "code-1", "display");
            static final TermCode PARENT_TERM_CODE_2 = new TermCode("parent", "code-2", "display");
            static final TermCode CHILD_TERM_CODE = new TermCode("child", "code", "display");
            static final TermCode CHILD_TERM_CODE_1 = new TermCode("child", "code-1", "display");
            static final TermCode CHILD_TERM_CODE_2 = new TermCode("child", "code-2", "display");
            static final TermCode CHILD_TERM_CODE_3 = new TermCode("child", "code-3", "display");
            static final TermCode CHILD_TERM_CODE_4 = new TermCode("child", "code-4", "display");
            static final TermCode CHILD_ATTR_TERM_CODE = new TermCode("child", "attr-code", "display");
            static final TermCode CHILD_ATTR_TERM_CODE_1 = new TermCode("child", "attr-code-1", "display");
            static final TermCode CHILD_ATTR_TERM_CODE_2 = new TermCode("child", "attr-code-2", "display");
            static final TermCode CHILD_ATTR_VALUE = new TermCode("child", "attr-value", "display");
            static final TermCode CHILD_ATTR_VALUE_1 = new TermCode("child", "attr-value-1", "display");
            static final TermCode CHILD_ATTR_VALUE_2 = new TermCode("child", "attr-value-2", "display");
            static final TermCode PARENT_FILTER_CODE = new TermCode("parent", "filter-code", "display");
            static final TermCode PARENT_FILTER_CODE_1 = new TermCode("parent", "filter-code-1", "display");
            static final TermCode PARENT_FILTER_CODE_2 = new TermCode("parent", "filter-code-2", "display");
            static final String P = "parent";
            static final String C = "child";


            @Nested
            @DisplayName("one concept expansion")
            class OneConceptExpansion {

                @BeforeEach
                void setUp() throws Exception {
                    when(mappingContext.expandConcept(cc(P))).thenReturn(ctcl(P));
                    when(mappingContext.findMapping(ctc(P))).thenReturn(Mapping.of(ctc(P),
                                    PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM)
                            .appendAttributeMapping(AttributeMapping.reference(PARENT_FILTER_CODE,
                                    PARENT_REFERENCE_FILTER_SEARCH_PARAM))
                            .appendAttributeMapping(AttributeMapping.reference(PARENT_FILTER_CODE_1,
                                    PARENT_REFERENCE_FILTER_SEARCH_PARAM_1))
                            .appendAttributeMapping(AttributeMapping.reference(PARENT_FILTER_CODE_2,
                                    PARENT_REFERENCE_FILTER_SEARCH_PARAM_2)));
                }

                @Nested
                @DisplayName("one reference attribute filter")
                class OneReferenceAttributeFilter {

                    @Test
                    @DisplayName("with wrong attribute mapping type")
                    void withWrongAttributeMappingType() throws Exception {
                        when(mappingContext.findMapping(ctc(P))).thenReturn(Mapping.of(ctc(P),
                                        PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM)
                                .appendAttributeMapping(AttributeMapping.code(PARENT_FILTER_CODE,
                                        PARENT_REFERENCE_FILTER_SEARCH_PARAM)));

                        assertThatThrownBy(() -> Criterion.of(cc(P))
                                .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                        Criterion.of(cc(C))))
                                .expand(mappingContext))
                .isInstanceOf(ConceptFilterTypeNotExpandableException.class);
                    }

                    @Nested
                    @DisplayName("one referenced criterion")
                    class OneReferencedCriterion {

                        @BeforeEach
                        void setUp() throws Exception {
                            when(mappingContext.expandConcept(cc(C))).thenReturn(ctcl(C));
                        }

                        @Test
                        @DisplayName("without any filters")
                        void withoutFilters() throws Exception {
                            when(mappingContext.findMapping(ctc(C))).thenReturn(Mapping.of(
                                    ctc(C), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, tc(C))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }

                        @Test
                        @DisplayName("with one fixed criterion")
                        void withOneFixedCriterion() throws Exception {
                            when(mappingContext.findMapping(ctc(C))).thenReturn(Mapping.of(
                                            ctc(C), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM)
                                    .withFixedCriteria(new FixedCriterion(FixedCriterionType.CODING,
                                            "verification-status", List.of(CONFIRMED), null)));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, PARENT_TERM_CODE)
                                            .appendFilter(ExpandedFilterGroup.of
                                                            (new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, CHILD_TERM_CODE),
                                                                    new ExpandedConceptFilter("verification-status", CONFIRMED))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }

                        @Test
                        @DisplayName("with one attribute filter")
                        void withOneAttributeFilter() throws Exception {
                            when(mappingContext.findMapping(ctc(C))).thenReturn(Mapping.of(
                                            ctc(C), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE,
                                            CHILD_ATTR_SEARCH_PARAM)));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C)).appendAttributeFilter(
                                                    AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE, CHILD_ATTR_VALUE))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, tc(C)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }

                        @Test
                        @DisplayName("with one attribute filter with two selected concepts")
                        void withOneAttributeFilterWithTwoSelectedConcepts() throws Exception {
                            when(mappingContext.findMapping(ctc(C))).thenReturn(Mapping.of(
                                            ctc(C), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE,
                                            CHILD_ATTR_SEARCH_PARAM)));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C)).appendAttributeFilter(
                                                    AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE, CHILD_ATTR_VALUE_1,
                                                            CHILD_ATTR_VALUE_2))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, tc(C)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, tc(C)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }

                        @Test
                        @DisplayName("with two attribute filters")
                        void withTwoAttributeFilters() throws Exception {
                            when(mappingContext.findMapping(ctc(C))).thenReturn(Mapping.of(
                                            ctc(C), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE_1,
                                            CHILD_ATTR_SEARCH_PARAM_1))
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE_2,
                                            CHILD_ATTR_SEARCH_PARAM_2)));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C))
                                                    .appendAttributeFilter(AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE_1,
                                                            CHILD_ATTR_VALUE_1))
                                                    .appendAttributeFilter(AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE_2,
                                                            CHILD_ATTR_VALUE_2))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, tc(C)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM_1, CHILD_ATTR_VALUE_1),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM_2, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }
                    }

                    @Nested
                    @DisplayName("two referenced criteria")
                    class TwoReferencedCriteria {

                        @BeforeEach
                        void setUp() throws Exception {
                            when(mappingContext.expandConcept(cc(C, 1))).thenReturn(ctcl(C, 1));
                            when(mappingContext.expandConcept(cc(C, 2))).thenReturn(ctcl(C, 2));
                        }

                        @Test
                        @DisplayName("without any filters")
                        void withoutFilters() throws Exception {
                            when(mappingContext.findMapping(ctc(C, 1))).thenReturn(Mapping.of(
                                    ctc(C, 1), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1));
                            when(mappingContext.findMapping(ctc(C, 2))).thenReturn(Mapping.of(
                                    ctc(C, 2), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C, 1)),
                                            Criterion.of(cc(C, 2))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1, tc(C, 1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2, tc(C, 2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }

                        @Test
                        @DisplayName("with one attribute filter at the first criterion")
                        void withOneAttributeFilterAtFirst() throws Exception {
                            when(mappingContext.findMapping(ctc(C, 1))).thenReturn(Mapping.of(
                                            ctc(C, 1), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE,
                                            CHILD_ATTR_SEARCH_PARAM)));
                            when(mappingContext.findMapping(ctc(C, 2))).thenReturn(Mapping.of(
                                    ctc(C, 2), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C, 1)).appendAttributeFilter(
                                                    AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE, CHILD_ATTR_VALUE)),
                                            Criterion.of(cc(C, 2))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1, tc(C, 1)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2, tc(C, 2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }

                        @Test
                        @DisplayName("with one attribute filter at the second criterion")
                        void withOneAttributeFilterAtSecond() throws Exception {
                            when(mappingContext.findMapping(ctc(C, 1))).thenReturn(Mapping.of(
                                    ctc(C, 1), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1));
                            when(mappingContext.findMapping(ctc(C, 2))).thenReturn(Mapping.of(
                                            ctc(C, 2), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE,
                                            CHILD_ATTR_SEARCH_PARAM)));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C, 1)),
                                            Criterion.of(cc(C, 2)).appendAttributeFilter(
                                                    AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE, CHILD_ATTR_VALUE))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1, tc(C, 1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2, tc(C, 2)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }

                        @Test
                        @DisplayName("with one attribute filter each")
                        void withOneAttributeFilter() throws Exception {
                            when(mappingContext.findMapping(ctc(C, 1))).thenReturn(Mapping.of(
                                            ctc(C, 1), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE_1,
                                            CHILD_ATTR_SEARCH_PARAM_1)));
                            when(mappingContext.findMapping(ctc(C, 2))).thenReturn(Mapping.of(
                                            ctc(C, 2), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE_2,
                                            CHILD_ATTR_SEARCH_PARAM_2)));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C, 1)).appendAttributeFilter(
                                                    AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE_1, CHILD_ATTR_VALUE_1)),
                                            Criterion.of(cc(C, 2)).appendAttributeFilter(
                                                    AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE_2, CHILD_ATTR_VALUE_2))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1, tc(C, 1)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM_1, CHILD_ATTR_VALUE_1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2, tc(C, 2)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM_2, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }

                        @Test
                        @DisplayName("with two attribute filters at the first criterion")
                        void withTwoAttributeFilterAtFirst() throws Exception {
                            when(mappingContext.findMapping(ctc(C, 1))).thenReturn(Mapping.of(
                                            ctc(C, 1), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE_1,
                                            CHILD_ATTR_SEARCH_PARAM_1))
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE_2,
                                            CHILD_ATTR_SEARCH_PARAM_2)));
                            when(mappingContext.findMapping(ctc(C, 2))).thenReturn(Mapping.of(
                                    ctc(C, 2), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C, 1))
                                                    .appendAttributeFilter(AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE_1,
                                                            CHILD_ATTR_VALUE_1))
                                                    .appendAttributeFilter(AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE_2,
                                                            CHILD_ATTR_VALUE_2)),
                                            Criterion.of(cc(C, 2))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1, tc(C, 1)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM_1, CHILD_ATTR_VALUE_1),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM_2, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2, tc(C, 2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }
                    }
                }

                @Nested
                @DisplayName("two reference attribute filter")
                class TwoReferenceAttributeFilter {

                    @BeforeEach
                    void setUp() throws Exception {
                        when(mappingContext.expandConcept(cc(C, 1))).thenReturn(ctcl(C, 1));
                        when(mappingContext.expandConcept(cc(C, 2))).thenReturn(ctcl(C, 2));
                    }

                    @Test
                    @DisplayName("without any filters")
                    void withoutFilters() throws Exception {
                        when(mappingContext.findMapping(ctc(C, 1))).thenReturn(Mapping.of(ctc(C, 1),
                                CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1));
                        when(mappingContext.findMapping(ctc(C, 2))).thenReturn(Mapping.of(ctc(C, 2),
                                CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2));

                        var criteria = Criterion.of(cc(P))
                                .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE_1,
                                        Criterion.of(cc(C, 1))))
                                .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE_2,
                                        Criterion.of(cc(C, 2))))
                                .expand(mappingContext);

                        assertThat(criteria).containsExactly(
                                ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                        .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1, tc(C, 1))
                                                .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM_1))
                                        .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2, tc(C, 2))
                                                .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM_2)));
                    }

                    @Test
                    @DisplayName("with one attribute filter at the first criterion")
                    void withOneAttributeFilterAtFirst() throws Exception {
                        when(mappingContext.findMapping(ctc(C, 1))).thenReturn(Mapping.of(ctc(C, 1),
                                CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1).appendAttributeMapping(
                                AttributeMapping.coding(CHILD_ATTR_TERM_CODE, CHILD_ATTR_SEARCH_PARAM)));
                        when(mappingContext.findMapping(ctc(C, 2))).thenReturn(Mapping.of(ctc(C, 2),
                                CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2));

                        var criteria = Criterion.of(cc(P))
                                .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE_1,
                                        Criterion.of(cc(C, 1)).appendAttributeFilter(
                                                AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE, CHILD_ATTR_VALUE))))
                                .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE_2,
                                        Criterion.of(cc(C, 2))))
                                .expand(mappingContext);

                        assertThat(criteria).containsExactly(
                                ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                        .appendFilter(ExpandedFilterGroup.of(
                                                        new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1, tc(C, 1)),
                                                        new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE))
                                                .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM_1))
                                        .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2, tc(C, 2))
                                                .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM_2)));
                    }

                    @Test
                    @DisplayName("with one attribute filter at the criterion of second reference filter")
                    void withOneAttributeFilterAtSecond() throws Exception {
                        when(mappingContext.findMapping(ctc(C, 1))).thenReturn(Mapping.of(ctc(C, 1),
                                CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1));
                        when(mappingContext.findMapping(ctc(C, 2))).thenReturn(Mapping.of(ctc(C, 2),
                                CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2).appendAttributeMapping(
                                AttributeMapping.coding(CHILD_ATTR_TERM_CODE, CHILD_ATTR_SEARCH_PARAM)));

                        var criteria = Criterion.of(cc(P))
                                .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE_1,
                                        Criterion.of(cc(C, 1))))
                                .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE_2,
                                        Criterion.of(cc(C, 2)).appendAttributeFilter(
                                                AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE, CHILD_ATTR_VALUE))))
                                .expand(mappingContext);

                        assertThat(criteria).containsExactly(
                                ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, PARENT_TERM_CODE)
                                        .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1, CHILD_TERM_CODE_1)
                                                .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM_1))
                                        .appendFilter(ExpandedFilterGroup.of(
                                                        new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2, CHILD_TERM_CODE_2),
                                                        new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE))
                                                .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM_2)));
                    }

                    @Test
                    @DisplayName("with two attribute filters at the criterion of first reference filter")
                    void withTwoAttributeFiltersAtFirst() throws Exception {
                        when(mappingContext.findMapping(ctc(C, 1))).thenReturn(Mapping.of(ctc(C, 1),
                                        CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1)
                                .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE_1, CHILD_ATTR_SEARCH_PARAM_1))
                                .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE_2, CHILD_ATTR_SEARCH_PARAM_2)));
                        when(mappingContext.findMapping(ctc(C, 2))).thenReturn(Mapping.of(ctc(C, 2),
                                CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2));

                        var criteria = Criterion.of(cc(P))
                                .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE_1,
                                        Criterion.of(cc(C, 1))
                                                .appendAttributeFilter(AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE_1, CHILD_ATTR_VALUE_1))
                                                .appendAttributeFilter(AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE_2, CHILD_ATTR_VALUE_2))))
                                .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE_2,
                                        Criterion.of(cc(C, 2))))
                                .expand(mappingContext);

                        assertThat(criteria).containsExactly(
                                ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, PARENT_TERM_CODE)
                                        .appendFilter(ExpandedFilterGroup.of(
                                                        new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1, CHILD_TERM_CODE_1),
                                                        new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM_1, CHILD_ATTR_VALUE_1),
                                                        new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM_2, CHILD_ATTR_VALUE_2))
                                                .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM_1))
                                        .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2, CHILD_TERM_CODE_2)
                                                .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM_2)));
                    }
                }

                @Nested
                @DisplayName("two referenced criteria at each reference filter")
                class TwoReferencedCriteria {

                    @BeforeEach
                    void setUp() throws Exception {
                        when(mappingContext.expandConcept(cc(C, 1))).thenReturn(List.of(ctc(C, 1)));
                        when(mappingContext.expandConcept(cc(C, 2))).thenReturn(List.of(ctc(C, 2)));
                        when(mappingContext.expandConcept(cc(C, 3))).thenReturn(List.of(ctc(C, 3)));
                        when(mappingContext.expandConcept(cc(C, 4))).thenReturn(List.of(ctc(C, 4)));
                    }

                    @Test
                    @DisplayName("without any filters")
                    void withoutFilters() throws Exception {
                        when(mappingContext.findMapping(ctc(C, 1))).thenReturn(Mapping.of(ctc(C, 1),
                                CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM));
                        when(mappingContext.findMapping(ctc(C, 2))).thenReturn(Mapping.of(ctc(C, 2),
                                CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM));
                        when(mappingContext.findMapping(ctc(C, 3))).thenReturn(Mapping.of(ctc(C, 3),
                                CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM));
                        when(mappingContext.findMapping(ctc(C, 4))).thenReturn(Mapping.of(ctc(C, 4),
                                CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM));

                        var criteria = Criterion.of(cc(P))
                                .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE_1,
                                        Criterion.of(cc(C, 1)),
                                        Criterion.of(cc(C, 2))))
                                .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE_2,
                                        Criterion.of(cc(C, 3)),
                                        Criterion.of(cc(C, 4))))
                                .expand(mappingContext);

                        assertThat(criteria).containsExactly(
                                ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, PARENT_TERM_CODE)
                                        .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, CHILD_TERM_CODE_1)
                                                .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM_1))
                                        .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, CHILD_TERM_CODE_3)
                                                .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM_2)),
                                ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, PARENT_TERM_CODE)
                                        .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, CHILD_TERM_CODE_1)
                                                .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM_1))
                                        .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, CHILD_TERM_CODE_4)
                                                .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM_2)),
                                ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, PARENT_TERM_CODE)
                                        .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, CHILD_TERM_CODE_2)
                                                .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM_1))
                                        .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, CHILD_TERM_CODE_3)
                                                .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM_2)),
                                ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, PARENT_TERM_CODE)
                                        .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, CHILD_TERM_CODE_2)
                                                .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM_1))
                                        .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, CHILD_TERM_CODE_4)
                                                .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM_2)));
                    }
                }
            }


            @Nested
            @DisplayName("two concept expansion at parent")
            class ParentTwoConceptExpansion {

                @BeforeEach
                void setUp() throws Exception {
                    when(mappingContext.expandConcept(cc(P))).thenReturn(ctcl(P, 1, 2));
                    when(mappingContext.findMapping(ctc(P, 1))).thenReturn(Mapping.of(ctc(P, 1),
                                    PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1)
                            .appendAttributeMapping(AttributeMapping.reference(PARENT_FILTER_CODE, PARENT_REFERENCE_FILTER_SEARCH_PARAM))
                            .appendAttributeMapping(AttributeMapping.reference(PARENT_FILTER_CODE_1, PARENT_REFERENCE_FILTER_SEARCH_PARAM_1))
                            .appendAttributeMapping(AttributeMapping.reference(PARENT_FILTER_CODE_2, PARENT_REFERENCE_FILTER_SEARCH_PARAM_2)));
                    when(mappingContext.findMapping(ctc(P, 2))).thenReturn(Mapping.of(ctc(P, 2),
                                    PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2)
                            .appendAttributeMapping(AttributeMapping.reference(PARENT_FILTER_CODE, PARENT_REFERENCE_FILTER_SEARCH_PARAM))
                            .appendAttributeMapping(AttributeMapping.reference(PARENT_FILTER_CODE_1, PARENT_REFERENCE_FILTER_SEARCH_PARAM_1))
                            .appendAttributeMapping(AttributeMapping.reference(PARENT_FILTER_CODE_2, PARENT_REFERENCE_FILTER_SEARCH_PARAM_2)));
                }

                @Nested
                @DisplayName("one reference attribute filter")
                class OneReferenceAttributeFilter {

                    @Nested
                    @DisplayName("one referenced criterion")
                    class OneReferencedCriterion {

                        @BeforeEach
                        void setUp() throws Exception {
                            when(mappingContext.expandConcept(cc(C))).thenReturn(ctcl(C));
                        }

                        @Test
                        @DisplayName("without any filters")
                        void withoutFilters() throws Exception {
                            when(mappingContext.findMapping(ctc(C))).thenReturn(Mapping.of(ctc(C),
                                    CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, tc(P, 1))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, tc(C))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, tc(P, 2))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, tc(C))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }

                        @Test
                        @DisplayName("with one attribute filter")
                        void withOneAttributeFilter() throws Exception {
                            when(mappingContext.findMapping(ctc(C))).thenReturn(Mapping.of(
                                            ctc(C), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE,
                                            CHILD_ATTR_SEARCH_PARAM)));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C)).appendAttributeFilter(
                                                    AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE, CHILD_ATTR_VALUE))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, tc(P, 1))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, tc(C)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, tc(P, 2))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, tc(C)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }

                        @Test
                        @DisplayName("with one attribute filter with two selected concepts")
                        void withOneAttributeFilterWithTwoSelectedConcepts() throws Exception {
                            when(mappingContext.findMapping(ctc(C))).thenReturn(Mapping.of(
                                            ctc(C), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE,
                                            CHILD_ATTR_SEARCH_PARAM)));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C)).appendAttributeFilter(
                                                    AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE, CHILD_ATTR_VALUE_1,
                                                            CHILD_ATTR_VALUE_2))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, tc(P, 1))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, tc(C)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, tc(P, 1))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, tc(C)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, tc(P, 2))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, tc(C)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, tc(P, 2))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, tc(C)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }

                        @Test
                        @DisplayName("with two attribute filters")
                        void withTwoAttributeFilters() throws Exception {
                            when(mappingContext.findMapping(ctc(C))).thenReturn(Mapping.of(
                                            ctc(C), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE_1,
                                            CHILD_ATTR_SEARCH_PARAM_1))
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE_2,
                                            CHILD_ATTR_SEARCH_PARAM_2)));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C))
                                                    .appendAttributeFilter(AttributeFilter.ofConcept(
                                                            CHILD_ATTR_TERM_CODE_1, CHILD_ATTR_VALUE_1))
                                                    .appendAttributeFilter(AttributeFilter.ofConcept(
                                                            CHILD_ATTR_TERM_CODE_2, CHILD_ATTR_VALUE_2))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, tc(P, 1))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, tc(C)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM_1, CHILD_ATTR_VALUE_1),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM_2, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, tc(P, 2))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM, tc(C)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM_1, CHILD_ATTR_VALUE_1),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM_2, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }
                    }

                    @Nested
                    @DisplayName("two referenced criteria")
                    class TwoReferencedCriteria {

                        @BeforeEach
                        void setUp() throws Exception {
                            when(mappingContext.expandConcept(cc(C, 1))).thenReturn(ctcl(C, 1));
                            when(mappingContext.expandConcept(cc(C, 2))).thenReturn(ctcl(C, 2));
                        }

                        @Test
                        @DisplayName("without any filters")
                        void withoutFilters() throws Exception {
                            when(mappingContext.findMapping(ctc(C, 1))).thenReturn(Mapping.of(
                                    ctc(C, 1), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1));
                            when(mappingContext.findMapping(ctc(C, 2))).thenReturn(Mapping.of(
                                    ctc(C, 2), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C, 1)),
                                            Criterion.of(cc(C, 2))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, tc(P, 1))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1, tc(C, 1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, tc(P, 1))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2, tc(C, 2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, tc(P, 2))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1, tc(C, 1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, tc(P, 2))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2, tc(C, 2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }
                    }
                }
            }


            @Nested
            @DisplayName("two concept expansion at child")
            class ChildTwoConceptExpansion {

                @BeforeEach
                void setUp() throws Exception {
                    when(mappingContext.expandConcept(cc(P))).thenReturn(ctcl(P));
                    when(mappingContext.findMapping(ctc(P))).thenReturn(Mapping.of(ctc(P),
                                    PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM)
                            .appendAttributeMapping(AttributeMapping.reference(PARENT_FILTER_CODE, PARENT_REFERENCE_FILTER_SEARCH_PARAM))
                            .appendAttributeMapping(AttributeMapping.reference(PARENT_FILTER_CODE_1, PARENT_REFERENCE_FILTER_SEARCH_PARAM_1))
                            .appendAttributeMapping(AttributeMapping.reference(PARENT_FILTER_CODE_2, PARENT_REFERENCE_FILTER_SEARCH_PARAM_2)));
                }

                @Nested
                @DisplayName("one reference attribute filter")
                class OneReferenceAttributeFilter {

                    @Nested
                    @DisplayName("one referenced criterion")
                    class OneReferencedCriterion {

                        @BeforeEach
                        void setUp() throws Exception {
                            when(mappingContext.expandConcept(cc(C))).thenReturn(ctcl(C, 1, 2));
                        }

                        @Test
                        @DisplayName("without any filters")
                        void withoutFilters() throws Exception {
                            when(mappingContext.findMapping(ctc(C, 1))).thenReturn(Mapping.of(
                                    ctc(C, 1), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1));
                            when(mappingContext.findMapping(ctc(C, 2))).thenReturn(Mapping.of(
                                    ctc(C, 2), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1, tc(C, 1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2, tc(C, 2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }

                        @Test
                        @DisplayName("with one attribute filter")
                        void withOneAttributeFilter() throws Exception {
                            when(mappingContext.findMapping(ctc(C, 1))).thenReturn(Mapping.of(
                                            ctc(C, 1), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE,
                                            CHILD_ATTR_SEARCH_PARAM_1)));
                            when(mappingContext.findMapping(ctc(C, 2))).thenReturn(Mapping.of(
                                            ctc(C, 2), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE,
                                            CHILD_ATTR_SEARCH_PARAM_2)));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C)).appendAttributeFilter(
                                                    AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE, CHILD_ATTR_VALUE))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1, tc(C, 1)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM_1, CHILD_ATTR_VALUE))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM, tc(P))
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2, tc(C, 2)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM_2, CHILD_ATTR_VALUE))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }
                    }
                }
            }

            @Nested
            @DisplayName("two concept expansion at parent and child")
            class ParentChildTwoConceptExpansion {

                @BeforeEach
                void setUp() throws Exception {
                    when(mappingContext.expandConcept(cc(P))).thenReturn(ctcl(P, 1, 2));
                    when(mappingContext.findMapping(ctc(P, 1))).thenReturn(Mapping.of(ctc(P, 1),
                                    PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1)
                            .appendAttributeMapping(AttributeMapping.reference(PARENT_FILTER_CODE, PARENT_REFERENCE_FILTER_SEARCH_PARAM))
                            .appendAttributeMapping(AttributeMapping.reference(PARENT_FILTER_CODE_1, PARENT_REFERENCE_FILTER_SEARCH_PARAM_1))
                            .appendAttributeMapping(AttributeMapping.reference(PARENT_FILTER_CODE_2, PARENT_REFERENCE_FILTER_SEARCH_PARAM_2)));
                    when(mappingContext.findMapping(ctc(P, 2))).thenReturn(Mapping.of(ctc(P, 2),
                                    PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2)
                            .appendAttributeMapping(AttributeMapping.reference(PARENT_FILTER_CODE, PARENT_REFERENCE_FILTER_SEARCH_PARAM))
                            .appendAttributeMapping(AttributeMapping.reference(PARENT_FILTER_CODE_1, PARENT_REFERENCE_FILTER_SEARCH_PARAM_1))
                            .appendAttributeMapping(AttributeMapping.reference(PARENT_FILTER_CODE_2, PARENT_REFERENCE_FILTER_SEARCH_PARAM_2)));
                }

                @Nested
                @DisplayName("one reference attribute filter")
                class OneReferenceAttributeFilter {

                    @Nested
                    @DisplayName("one referenced criterion")
                    class OneReferencedCriterion {

                        @BeforeEach
                        void setUp() throws Exception {
                            when(mappingContext.expandConcept(cc(C))).thenReturn(ctcl(C, 1, 2));
                        }

                        @Test
                        @DisplayName("without any filters")
                        void withoutFilters() throws Exception {
                            when(mappingContext.findMapping(ctc(C, 1))).thenReturn(Mapping.of(
                                    ctc(C, 1), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1));
                            when(mappingContext.findMapping(ctc(C, 2))).thenReturn(Mapping.of(
                                    ctc(C, 2), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, tc(P, 1))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1, tc(C, 1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, tc(P, 1))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2, tc(C, 2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, tc(P, 2))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1, tc(C, 1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, tc(P, 2))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2, tc(C, 2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }
                    }

                    @Nested
                    @DisplayName("two referenced criteria")
                    class TwoReferencedCriteria {

                        static final String CHILD_CODE_SEARCH_PARAM_1_1 = "child-code-search-param-1-1";
                        static final String CHILD_CODE_SEARCH_PARAM_1_2 = "child-code-search-param-1-2";
                        static final String CHILD_CODE_SEARCH_PARAM_2_1 = "child-code-search-param-2-1";
                        static final String CHILD_CODE_SEARCH_PARAM_2_2 = "child-code-search-param-2-2";

                        @BeforeEach
                        void setUp() throws Exception {
                            when(mappingContext.expandConcept(cc(C, 1))).thenReturn(ctcl(C, 11, 12));
                            when(mappingContext.expandConcept(cc(C, 2))).thenReturn(ctcl(C, 21, 22));
                        }

                        @Test
                        @DisplayName("without any filters")
                        void withoutFilters() throws Exception {
                            when(mappingContext.findMapping(ctc(C, 11))).thenReturn(Mapping.of(
                                    ctc(C, 11), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1_1));
                            when(mappingContext.findMapping(ctc(C, 12))).thenReturn(Mapping.of(
                                    ctc(C, 12), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1_2));
                            when(mappingContext.findMapping(ctc(C, 21))).thenReturn(Mapping.of(
                                    ctc(C, 21), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2_1));
                            when(mappingContext.findMapping(ctc(C, 22))).thenReturn(Mapping.of(
                                    ctc(C, 22), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2_2));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C, 1)),
                                            Criterion.of(cc(C, 2))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, tc(P, 1))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1_1, tc(C, 11))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, tc(P, 1))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1_2, tc(C, 12))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, tc(P, 1))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2_1, tc(C, 21))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, tc(P, 1))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2_2, tc(C, 22))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, tc(P, 2))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1_1, tc(C, 11))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, tc(P, 2))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1_2, tc(C, 12))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, tc(P, 2))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2_1, tc(C, 21))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, tc(P, 2))
                                            .appendFilter(new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2_2, tc(C, 22))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }

                        @Test
                        @DisplayName("with one attribute filter")
                        void withOneAttributeFilter() throws Exception {
                            when(mappingContext.findMapping(ctc(C, 11))).thenReturn(Mapping.of(
                                            ctc(C, 11), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1_1)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE,
                                            CHILD_ATTR_SEARCH_PARAM)));
                            when(mappingContext.findMapping(ctc(C, 12))).thenReturn(Mapping.of(
                                            ctc(C, 12), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1_2)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE,
                                            CHILD_ATTR_SEARCH_PARAM)));
                            when(mappingContext.findMapping(ctc(C, 21))).thenReturn(Mapping.of(
                                            ctc(C, 21), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2_1)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE,
                                            CHILD_ATTR_SEARCH_PARAM)));
                            when(mappingContext.findMapping(ctc(C, 22))).thenReturn(Mapping.of(
                                            ctc(C, 22), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2_2)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE,
                                            CHILD_ATTR_SEARCH_PARAM)));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C, 1)).appendAttributeFilter(
                                                    AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE, CHILD_ATTR_VALUE_1)),
                                            Criterion.of(cc(C, 2)).appendAttributeFilter(
                                                    AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE, CHILD_ATTR_VALUE_2))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, PARENT_TERM_CODE_1)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1_1, tc(C, 11)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, PARENT_TERM_CODE_1)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1_2, tc(C, 12)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, PARENT_TERM_CODE_1)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2_1, tc(C, 21)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, PARENT_TERM_CODE_1)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2_2, tc(C, 22)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, PARENT_TERM_CODE_2)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1_1, tc(C, 11)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, PARENT_TERM_CODE_2)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1_2, tc(C, 12)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, PARENT_TERM_CODE_2)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2_1, tc(C, 21)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, PARENT_TERM_CODE_2)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2_2, tc(C, 22)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }

                        @Test
                        @DisplayName("with one attribute filter with two selected concepts")
                        void withOneAttributeFilterWithTwoSelectedConcepts() throws Exception {
                            when(mappingContext.findMapping(ctc(C, 11))).thenReturn(Mapping.of(
                                            ctc(C, 11), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1_1)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE,
                                            CHILD_ATTR_SEARCH_PARAM)));
                            when(mappingContext.findMapping(ctc(C, 12))).thenReturn(Mapping.of(
                                            ctc(C, 12), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_1_2)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE,
                                            CHILD_ATTR_SEARCH_PARAM)));
                            when(mappingContext.findMapping(ctc(C, 21))).thenReturn(Mapping.of(
                                            ctc(C, 21), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2_1)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE,
                                            CHILD_ATTR_SEARCH_PARAM)));
                            when(mappingContext.findMapping(ctc(C, 22))).thenReturn(Mapping.of(
                                            ctc(C, 22), CHILD_RESOURCE_TYPE, CHILD_CODE_SEARCH_PARAM_2_2)
                                    .appendAttributeMapping(AttributeMapping.coding(CHILD_ATTR_TERM_CODE,
                                            CHILD_ATTR_SEARCH_PARAM)));

                            var criteria = Criterion.of(cc(P))
                                    .appendAttributeFilter(AttributeFilter.ofReference(PARENT_FILTER_CODE,
                                            Criterion.of(cc(C, 1)).appendAttributeFilter(
                                                    AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE, CHILD_ATTR_VALUE_1,
                                                            CHILD_ATTR_VALUE_2)),
                                            Criterion.of(cc(C, 2)).appendAttributeFilter(
                                                    AttributeFilter.ofConcept(CHILD_ATTR_TERM_CODE, CHILD_ATTR_VALUE_1,
                                                            CHILD_ATTR_VALUE_2))))
                                    .expand(mappingContext);

                            assertThat(criteria).containsExactly(
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, PARENT_TERM_CODE_1)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1_1, tc(C, 11)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, PARENT_TERM_CODE_1)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1_1, tc(C, 11)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, PARENT_TERM_CODE_1)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1_2, tc(C, 12)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, PARENT_TERM_CODE_1)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1_2, tc(C, 12)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, PARENT_TERM_CODE_1)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2_1, tc(C, 21)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, PARENT_TERM_CODE_1)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2_1, tc(C, 21)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, PARENT_TERM_CODE_1)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2_2, tc(C, 22)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_1, PARENT_TERM_CODE_1)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2_2, tc(C, 22)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, PARENT_TERM_CODE_2)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1_1, tc(C, 11)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, PARENT_TERM_CODE_2)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1_1, tc(C, 11)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, PARENT_TERM_CODE_2)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1_2, tc(C, 12)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, PARENT_TERM_CODE_2)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_1_2, tc(C, 12)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, PARENT_TERM_CODE_2)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2_1, tc(C, 21)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, PARENT_TERM_CODE_2)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2_1, tc(C, 21)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, PARENT_TERM_CODE_2)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2_2, tc(C, 22)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_1))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)),
                                    ExpandedCriterion.of(PARENT_RESOURCE_TYPE, PARENT_CODE_SEARCH_PARAM_2, PARENT_TERM_CODE_2)
                                            .appendFilter(ExpandedFilterGroup.of(
                                                            new ExpandedConceptFilter(CHILD_CODE_SEARCH_PARAM_2_2, tc(C, 22)),
                                                            new ExpandedConceptFilter(CHILD_ATTR_SEARCH_PARAM, CHILD_ATTR_VALUE_2))
                                                    .chain(PARENT_REFERENCE_FILTER_SEARCH_PARAM)));
                        }
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("special patient criteria")
    class PatientCriteria {

        static final TermCode GENDER = new TermCode("http://snomed.info/sct", "263495000", "Geschlecht");

        ExpandedCriterion expandedCriterion = ExpandedCriterion.of("Patient");

        @Test
        void gender() throws Exception {
            when(mappingContext.expandConcept(ContextualConcept.of(CONTEXT, Concept.of(GENDER)))).thenReturn(
                    (List.of(ContextualTermCode.of(CONTEXT, GENDER))));
            when(mappingContext.findMapping(ContextualTermCode.of(CONTEXT, GENDER))).thenReturn(
                    Mapping.of(ContextualTermCode.of(CONTEXT, GENDER), "Patient")
                            .withValueFilterMapping(FilterMappingType.CODE, "gender"));

            var criterion = Criterion.of(ContextualConcept.of(CONTEXT, Concept.of(GENDER)),
                    ValueFilter.ofConcept(MALE)).expand(mappingContext);

            assertThat(criterion).containsExactly(expandedCriterion
                    .appendFilter(new ExpandedCodeFilter("gender", "male")));
        }

        @Nested
        @DisplayName("age")
        class Age {

            static final ContextualTermCode CONTEXTUAL_AGE = ContextualTermCode.of(CONTEXT, AGE);
            static final ContextualConcept CONTEXTUAL_CONCEPT_AGE = ContextualConcept.of(CONTEXT, Concept.of(AGE));
            static final LocalDate YEAR_2000 = LocalDate.of(2000, 1, 1);
            static final LocalDate YEAR_1990 = LocalDate.of(1990, 1, 1);
            static final LocalDate YEAR_1995 = LocalDate.of(1995, 1, 1);
            static final LocalDate YEAR_1994 = LocalDate.of(1994, 1, 1);
            static final BigDecimal AGE_OF_10 = BigDecimal.valueOf(10);

            Mapping mapping = Mapping.of(CONTEXTUAL_AGE, "Patient").withValueFilterMapping(FilterMappingType.AGE,
                    "birthdate");

            @BeforeEach
            void setUp() throws Exception {
                when(mappingContext.expandConcept(CONTEXTUAL_CONCEPT_AGE)).thenReturn(
                        (List.of(CONTEXTUAL_AGE)));
            }

            @Test
            @DisplayName("with comparator equal")
            void age_withComparatorEqual() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_AGE)).thenReturn(mapping);
                when(mappingContext.today()).thenReturn(YEAR_2000);

                var criterion = Criterion.of(CONTEXTUAL_CONCEPT_AGE, ValueFilter.ofComparator(EQUAL, Quantity.of(
                        AGE_OF_5, YEAR_UNIT))).expand(mappingContext);

                assertThat(criterion).containsExactly(expandedCriterion
                        .appendFilter(new ExpandedDateRangeFilter("birthdate", YEAR_1994.plusDays(1), YEAR_1995)));
            }

            @ParameterizedTest
            @EnumSource(names = {"EQUAL"}, mode = EXCLUDE)
            @DisplayName("with other comparator")
            void age_withOtherComparator(Comparator comparator) throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_AGE)).thenReturn(mapping);
                when(mappingContext.today()).thenReturn(YEAR_2000);

                var criterion = Criterion.of(CONTEXTUAL_CONCEPT_AGE, ValueFilter.ofComparator(comparator,
                        Quantity.of(AGE_OF_5, YEAR_UNIT))).expand(mappingContext);

                assertThat(criterion).containsExactly(expandedCriterion
                        .appendFilter(new ExpandedDateComparatorFilter("birthdate", comparator.reverse(), YEAR_1995)));
            }

            @Test
            @DisplayName("with greater-than comparator and week unit")
            void age_withWeekUnit() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_AGE)).thenReturn(mapping);
                when(mappingContext.today()).thenReturn(YEAR_2000);

                var criterion = Criterion.of(CONTEXTUAL_CONCEPT_AGE, ValueFilter.ofComparator(GREATER_THAN,
                        Quantity.of(AGE_OF_5, WEEK_UNIT))).expand(mappingContext);

                assertThat(criterion).containsExactly(expandedCriterion
                        .appendFilter(new ExpandedDateComparatorFilter("birthdate", LESS_THAN,
                                YEAR_2000.minusWeeks(AGE_OF_5.longValue()))));
            }

            @Test
            @DisplayName("with greater-than comparator and month unit")
            void age_withMonthUnit() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_AGE)).thenReturn(mapping);
                when(mappingContext.today()).thenReturn(YEAR_2000);

                var criterion = Criterion.of(CONTEXTUAL_CONCEPT_AGE, ValueFilter.ofComparator(GREATER_THAN,
                        Quantity.of(AGE_OF_5, MONTH_UNIT))).expand(mappingContext);

                assertThat(criterion).containsExactly(expandedCriterion
                        .appendFilter(new ExpandedDateComparatorFilter("birthdate", LESS_THAN,
                                YEAR_2000.minusMonths(AGE_OF_5.longValue()))));
            }

            @Test
            @DisplayName("without unit in lower bound")
            void age_RangeWithoutLowerUnit() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_AGE)).thenReturn(mapping);

                assertThatThrownBy(() -> Criterion.of(CONTEXTUAL_CONCEPT_AGE, ValueFilter.ofRange(Quantity.of(AGE_OF_5),
                        Quantity.of(AGE_OF_10, YEAR_UNIT))).expand(mappingContext))
                .isInstanceOf(CalculationException.class);
            }

            @Test
            @DisplayName("without unit in upper bound")
            void age_RangeWithoutUpperUnit() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_AGE)).thenReturn(mapping);

                assertThatThrownBy(() -> Criterion.of(CONTEXTUAL_CONCEPT_AGE, ValueFilter.ofRange(Quantity.of(AGE_OF_5,
                        YEAR_UNIT), Quantity.of(AGE_OF_10))).expand(mappingContext))
                .isInstanceOf(CalculationException.class);
            }

            @Test
            @DisplayName("with comparator and unknown unit")
            void age_withComparator_UnknownUnit() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_AGE)).thenReturn(mapping);
                when(mappingContext.today()).thenReturn(YEAR_2000);

                assertThatThrownBy(() -> Criterion.of(CONTEXTUAL_CONCEPT_AGE, ValueFilter.ofComparator(GREATER_THAN,
                        Quantity.of(AGE_OF_5, GRAM_PER_DECILITER))).expand(mappingContext))
                .isInstanceOf(CalculationException.class);
            }

            @Test
            @DisplayName("with comparator and without unit")
            void age_withComparator_WithoutUnit() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_AGE)).thenReturn(mapping);

                assertThatThrownBy(() -> Criterion.of(CONTEXTUAL_CONCEPT_AGE, ValueFilter.ofComparator(GREATER_THAN,
                        Quantity.of(AGE_OF_5))).expand(mappingContext))
                .isInstanceOf(CalculationException.class);
            }

            @Test
            @DisplayName("with range")
            void age_withRange() throws Exception {
                when(mappingContext.findMapping(CONTEXTUAL_AGE)).thenReturn(mapping);
                when(mappingContext.today()).thenReturn(YEAR_2000);

                var criterion = Criterion.of(CONTEXTUAL_CONCEPT_AGE, ValueFilter.ofRange(Quantity.of(AGE_OF_5,
                                YEAR_UNIT), Quantity.of(AGE_OF_10, YEAR_UNIT)))
                        .expand(mappingContext);

                assertThat(criterion).containsExactly(expandedCriterion
                        .appendFilter(new ExpandedDateRangeFilter("birthdate", YEAR_1990, YEAR_1995)));
            }
        }
    }
}
