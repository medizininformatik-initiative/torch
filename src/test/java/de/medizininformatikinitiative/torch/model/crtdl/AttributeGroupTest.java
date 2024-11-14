package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.codeValue;
import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.dateValue;
import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static de.medizininformatikinitiative.torch.model.sq.Comparator.GREATER_EQUAL;
import static de.medizininformatikinitiative.torch.model.sq.Comparator.LESS_EQUAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttributeGroupTest {

    static final LocalDate DATE_START = LocalDate.parse("2023-01-01");
    static final LocalDate DATE_END = LocalDate.parse("2023-12-31");
    static final Code CODE1 = new Code("system1", "code1");
    static final Code CODE2 = new Code("system2", "code2");

    @Test
    void testDuplicateDateFiltersThrowsException() {
        var dateFilter1 = new Filter("date", "dateField1", DATE_START, DATE_END);
        var dateFilter2 = new Filter("date", "dateField2", DATE_START, DATE_END);

        assertThatThrownBy(() -> new AttributeGroup("groupRef", List.of(), List.of(dateFilter1, dateFilter2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate date type filter found");
    }

    @Nested
    class ParseJson {
        ObjectMapper mapper;

        @BeforeEach
        void setup() {
            mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
        }

        @Test
        void parseJson() throws JsonProcessingException {

            var result = mapper.readValue("""
                     {"groupReference": "test",
                            "attributes": [
                              {
                                "attributeRef": "Condition.code",
                                "mustHave": false
                              }
                            ]
                    }
                    """, AttributeGroup.class);

            assertThat(result.groupReference()).isEqualTo("test");
            assertThat(result.attributes()).containsExactly(new Attribute("Condition.code", false));
        }

        @Test
        void parseJsonSuccess() throws JsonProcessingException {

            var result = mapper.readValue("""
                    {
                      "groupReference": "test",
                      "attributes": [
                        {
                          "attributeRef": "Condition.code",
                          "mustHave": false
                        }
                      ],
                      "filter": [
                        {
                          "type": "token",
                          "name": "code",
                          "codes": [
                            {
                              "code": "45252009",
                              "system": "http://snomed.info/sct",
                              "display": "test"
                            }
                          ]
                        },
                        {
                          "type": "date",
                          "name": "date",
                          "start": "2021-01-01",
                          "end": "2025-01-01"
                        }
                      ]
                    }
                    """, AttributeGroup.class);

            assertThat(result.groupReference()).isEqualTo("test");
            assertThat(result.attributes()).containsExactly(new Attribute("Condition.code", false));
            assertThat(result.filter()).containsExactly(
                    new Filter("token", "code", List.of(new Code("http://snomed.info/sct", "45252009"))),
                    new Filter("date", "date", LocalDate.parse("2021-01-01"), LocalDate.parse("2025-01-01"))
            );
        }

        @Test
        void parseJson2DatesFail() {
            assertThatThrownBy(() -> mapper.readValue("""
                     {
                       "groupReference": "test",
                       "attributes": [
                         {
                           "attributeRef": "Condition.code",
                           "mustHave": false
                         }
                       ],
                       "filter": [
                         {
                           "type": "date",
                           "name": "date",
                           "start": "2021-01-01",
                           "end": "2025-01-01"
                         },
                         {
                           "type": "date",
                           "name": "date2",
                           "start": "2022-01-01",
                           "end": "2026-01-01"
                         }
                       ]
                     }
                    """, AttributeGroup.class)).isInstanceOf(JsonProcessingException.class)
                    .hasMessageStartingWith("Cannot construct instance of `de.medizininformatikinitiative.torch.model.crtdl.AttributeGroup`, problem: Duplicate date type filter found");
        }


    }


    @Nested
    class Queries {

        @Mock
        DseMappingTreeBase mappingTreeBase;

        @Test
        void oneCode() {
            when(mappingTreeBase.expand("system1", "code1")).thenReturn(Stream.of("code1"));
            var tokenFilter = new Filter("token", "code", List.of(CODE1));
            var attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Patient.name", false)), List.of(tokenFilter));

            var result = attributeGroup.queries(mappingTreeBase);

            assertThat(result).containsExactly(
                    new Query("Patient", QueryParams.of("code", codeValue(CODE1)).appendParam("_profile", stringValue("groupRef")))
            );
        }

        @Test
        void twoCodes() {
            when(mappingTreeBase.expand("system1", "code1")).thenReturn(Stream.of("code1"));
            when(mappingTreeBase.expand("system2", "code2")).thenReturn(Stream.of("code2"));
            var tokenFilter = new Filter("token", "code", List.of(CODE1, CODE2));
            var attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Patient.name", false)), List.of(tokenFilter));

            var result = attributeGroup.queries(mappingTreeBase);

            assertThat(result).containsExactly(
                    new Query("Patient", QueryParams.of("code", codeValue(CODE1)).appendParam("_profile", stringValue("groupRef"))),
                    new Query("Patient", QueryParams.of("code", codeValue(CODE2)).appendParam("_profile", stringValue("groupRef")))
            );
        }

        @Test
        void dateFilter() {
            var dateFilter = new Filter("date", "date", DATE_START, DATE_END);
            var attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Observation.name", false)), List.of(dateFilter));

            var result = attributeGroup.queries(mappingTreeBase);

            assertThat(result).containsExactly(
                    new Query("Observation", QueryParams.of("date", dateValue(GREATER_EQUAL, DATE_START)).appendParam("date", dateValue(LESS_EQUAL, DATE_END)).appendParam("_profile", stringValue("groupRef")))
            );
        }

        @Test
        void dateFilterIgnoredForPatients() {
            var dateFilter = new Filter("date", "date", DATE_START, DATE_END);
            var attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Patient.name", false)), List.of(dateFilter));

            var result = attributeGroup.queries(mappingTreeBase);

            assertThat(result).containsExactly(
                    new Query("Patient", QueryParams.of("_profile", stringValue("groupRef")))
            );
        }
    }

    @Nested
    class HasMustHave {

        @Test
        void testTrue() {
            var attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Patient.name", true)), List.of());

            assertThat(attributeGroup.hasMustHave()).isTrue();
        }

        @Test
        void testFalse() {
            var attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Patient.name", false)), List.of());

            assertThat(attributeGroup.hasMustHave()).isFalse();
        }
    }O

    @Nested
    class StandardAttributes {

        @Test
        void patient() {
            var attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Patient.name", false)), List.of());

            var standardAddedGroup = attributeGroup.addStandardAttributes(Patient.class);

            assertThat(standardAddedGroup.hasMustHave()).isTrue();
            assertThat(standardAddedGroup.attributes()).containsExactly(
                    new Attribute("Patient.name", false),
                    new Attribute("Patient.id", true),
                    new Attribute("Patient.meta.profile", true))
            ;

        }

        @Test
        void consent() {
            var attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Consent.identifier", false)), List.of());

            var standardAddedGroup = attributeGroup.addStandardAttributes(Consent.class);

            assertThat(standardAddedGroup.hasMustHave()).isTrue();
            assertThat(standardAddedGroup.attributes()).containsExactly(
                    new Attribute("Consent.identifier", false),
                    new Attribute("Consent.id", true),
                    new Attribute("Consent.meta.profile", true),
                    new Attribute("Consent.patient.reference", true)
            );

        }

        @Test
        void observation() {
            var attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Observation.identifier", false)), List.of());

            var standardAddedGroup = attributeGroup.addStandardAttributes(Observation.class);

            assertThat(standardAddedGroup.hasMustHave()).isTrue();
            assertThat(standardAddedGroup.attributes()).containsExactly(
                    new Attribute("Observation.identifier", false),
                    new Attribute("Observation.id", true),
                    new Attribute("Observation.meta.profile", true),
                    new Attribute("Observation.subject.reference", true),
                    new Attribute("Observation.status", true)
            );

        }

        @Test
        void defaultCase() {
            var attributeGroup = new AttributeGroup("groupRef", List.of(new Attribute("Condition.code", false)), List.of());

            var standardAddedGroup = attributeGroup.addStandardAttributes(Condition.class);

            assertThat(standardAddedGroup.hasMustHave()).isTrue();
            assertThat(standardAddedGroup.attributes()).containsExactly(
                    new Attribute("Condition.code", false),
                    new Attribute("Condition.id", true),
                    new Attribute("Condition.meta.profile", true),
                    new Attribute("Condition.subject.reference", true)
            );

        }


    }
}
