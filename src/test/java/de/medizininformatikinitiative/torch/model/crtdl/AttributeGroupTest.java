package de.medizininformatikinitiative.torch.model.crtdl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThatThrownBy(() -> new AttributeGroup("test", "groupRef", List.of(), List.of(dateFilter1, dateFilter2)))
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
                     {"id":"testID",
                     "groupReference": "test",
                            "attributes": [
                              {
                                "attributeRef": "Condition.code",
                                "mustHave": false
                              }
                            ]
                    }
                    """, AttributeGroup.class);
            assertThat(result.id()).isEqualTo("testID");
            assertThat(result.groupReference()).isEqualTo("test");
            assertThat(result.attributes()).containsExactly(new Attribute("Condition.code", false));
        }

        @Test
        void parseJsonSuccess() throws JsonProcessingException {

            var result = mapper.readValue("""
                    {  "id":"testID",
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
            assertThat(result.id()).isEqualTo("testID");

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
                     "id": "testID",
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
    class HasMustHave {

        @Test
        void testTrue() {
            var attributeGroup = new AttributeGroup("test", "groupRef", List.of(new Attribute("Patient.name", true)), List.of());

            assertThat(attributeGroup.hasMustHave()).isTrue();
        }

        @Test
        void testFalse() {
            var attributeGroup = new AttributeGroup("test", "groupRef", List.of(new Attribute("Patient.name", false)), List.of());

            assertThat(attributeGroup.hasMustHave()).isFalse();
        }
    }


}
