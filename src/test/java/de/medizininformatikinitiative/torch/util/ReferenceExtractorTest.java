package de.medizininformatikinitiative.torch.util;

import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReferenceExtractorTest {

    public static final AnnotatedAttribute ATTRIBUTE_OPTIONAL = new AnnotatedAttribute("Condition.subject", "Condition.subject", false, List.of("SubjectGroup"));
    public static final AnnotatedAttribute ATTRIBUTE_MUST_HAVE = new AnnotatedAttribute("Condition.subject", "Condition.subject", true, List.of("SubjectGroup"));
    public static final AnnotatedAttribute ATTRIBUTE_RESOURCE = new AnnotatedAttribute("Condition", "Condition", true, List.of("SubjectGroup"));
    public static final AnnotatedAttribute ATTRIBUTE_DIAGNOSIS = new AnnotatedAttribute("Encounter.diagnosis", "Encounter.diagnosis", true, List.of("SubjectGroup"));
    public static final AnnotatedAttribute ATTRIBUTE_2 = new AnnotatedAttribute("Condition.asserter", "Condition.asserter", true, List.of("AssertionGroup"));
    public static final String DIAG_URL = "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose";
    public static ReferenceExtractor referenceExtractor;
    static AnnotatedAttributeGroup GROUP_TEST = new AnnotatedAttributeGroup("Test", "Condition", DIAG_URL, List.of(ATTRIBUTE_MUST_HAVE, ATTRIBUTE_2), List.of());
    static AnnotatedAttributeGroup GROUP_INVALID = new AnnotatedAttributeGroup("Test", "Condition", "invalid", List.of(ATTRIBUTE_MUST_HAVE, ATTRIBUTE_2), List.of());

    static Map<String, AnnotatedAttributeGroup> GROUPS = new HashMap<>();
    private static IntegrationTestSetup itSetup;


    private final String encounterString = """
                {
              "resourceType": "Encounter",
              "id": "torch-test-diag-enc-diag-enc-1",
              "meta": {
                "versionId": "2",
                "lastUpdated": "2025-11-09T12:56:38.896Z",
                "profile": [
                  "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung"
                ]
              },
              "identifier": [
                {
                  "type": {
                    "coding": [
                      {
                        "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
                        "code": "VN"
                      }
                    ]
                  },
                  "system": "https://www.charite.de/fhir/NamingSystem/Aufnahmenummern",
                  "value": "MII_0000001"
                }
              ],
              "status": "finished",
              "class": {
                "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                "code": "IMP"
              },
              "type": [
                {
                  "coding": [
                    {
                      "system": "http://fhir.de/CodeSystem/Kontaktebene",
                      "code": "einrichtungskontakt"
                    }
                  ]
                },
                {
                  "coding": [
                    {
                      "system": "http://fhir.de/CodeSystem/kontaktart-de",
                      "code": "normalstationaer"
                    }
                  ]
                }
              ],
              "serviceType": {
                "coding": [
                  {
                    "system": "http://fhir.de/CodeSystem/dkgev/Fachabteilungsschluessel",
                    "code": "0100"
                  }
                ]
              },
              "subject": {
                "reference": "Patient/torch-test-diag-enc-diag-pat-1"
              },
              "period": {
                "start": "2024-02-14",
                "end": "2024-02-22"
              },
              "diagnosis": [
                {
                  "condition": {
                    "reference": "Condition/torch-test-diag-enc-diag-diag-1"
                  },
                  "use": {
                    "coding": [
                      {
                        "system": "http://terminology.hl7.org/CodeSystem/diagnosis-role",
                        "code": "AD",
                        "display": "Admission diagnosis"
                      }
                    ]
                  },
                  "rank": 1
                },
                {
                  "condition": {
                    "reference": "Condition/torch-test-diag-enc-diag-diag-2"
                  },
                  "use": {
                    "coding": [
                      {
                        "system": "http://terminology.hl7.org/CodeSystem/diagnosis-role",
                        "code": "AD",
                        "display": "Admission diagnosis"
                      }
                    ]
                  },
                  "rank": 2
                }
              ],
              "location": [
                {
                  "location": {
                    "identifier": {
                      "system": "https://www.charite.de/fhir/sid/Zimmernummern",
                      "value": "RHC-06-210b"
                    },
                    "display": "RHC-06-210b"
                  },
                  "physicalType": {
                    "coding": [
                      {
                        "system": "http://terminology.hl7.org/CodeSystem/location-physical-type",
                        "code": "ro"
                      }
                    ]
                  }
                },
                {
                  "location": {
                    "identifier": {
                      "system": "https://www.charite.de/fhir/sid/Bettennummern",
                      "value": "RHC-06-210b-02"
                    },
                    "display": "RHC-06-210b-02"
                  },
                  "physicalType": {
                    "coding": [
                      {
                        "system": "http://terminology.hl7.org/CodeSystem/location-physical-type",
                        "code": "bd"
                      }
                    ]
                  }
                },
                {
                  "location": {
                    "identifier": {
                      "system": "https://www.charite.de/fhir/sid/Stationsnummern",
                      "value": "RHC-06"
                    },
                    "display": "RHC-06"
                  },
                  "physicalType": {
                    "coding": [
                      {
                        "system": "http://terminology.hl7.org/CodeSystem/location-physical-type",
                        "code": "wa"
                      }
                    ]
                  }
                }
              ]
            }
            """;

    @BeforeAll
    static void setup() throws IOException {
        GROUPS.put("Test", GROUP_TEST);
        GROUPS.put("Invalid", GROUP_INVALID);
        itSetup = new IntegrationTestSetup();
        referenceExtractor = new ReferenceExtractor(itSetup.fhirContext());

    }

    @Nested
    class getReferences {
        @Test
        void successDirectReference() throws MustHaveViolatedException {
            Condition condition = new Condition();
            condition.setId("Condition1");
            condition.setSubject(new Reference("Patient1"));
            assertThat(referenceExtractor.getReferences(condition, ATTRIBUTE_MUST_HAVE)).containsExactly("Patient1");
        }

        @Test
        void optionalElementDoesNotTriggerMustHaveViolation() throws MustHaveViolatedException {
            Condition condition = new Condition();
            condition.setId("Condition1");
            itSetup.structureDefinitionHandler().getDefinition(DIAG_URL);
            assertThat(referenceExtractor.getReferences(condition, ATTRIBUTE_OPTIONAL)).isEmpty();
        }

        @Test
        void mustHaveViolated() {
            Condition condition = new Condition();
            condition.setId("Condition1");
            itSetup.structureDefinitionHandler().getDefinition(DIAG_URL);
            assertThatThrownBy(() -> referenceExtractor.getReferences(condition, ATTRIBUTE_MUST_HAVE)).isInstanceOf(MustHaveViolatedException.class);
        }

        @Test
        void successRecursive() throws MustHaveViolatedException {
            Condition condition = new Condition();
            condition.setId("Condition1");
            condition.setSubject(new Reference("Patient1"));

            assertThat(referenceExtractor.getReferences(condition, ATTRIBUTE_RESOURCE)).containsExactly("Patient1");
        }

        @Test
        void successRecursiveEncounter() throws MustHaveViolatedException {
            Encounter encounter = new Encounter();
            encounter.setId("Encounter1");
            encounter.setDiagnosis(List.of(new Encounter.DiagnosisComponent().setCondition(new Reference("Condition1"))));
            assertThat(referenceExtractor.getReferences(encounter, ATTRIBUTE_DIAGNOSIS)).containsExactly("Condition1");
        }

        @Test
        void successRecursiveEncounter2() throws MustHaveViolatedException {
            Encounter encounter = itSetup.fhirContext().newJsonParser().parseResource(Encounter.class, encounterString);
            assertThat(referenceExtractor.getReferences(encounter, ATTRIBUTE_DIAGNOSIS)).containsExactly("Condition/torch-test-diag-enc-diag-diag-1", "Condition/torch-test-diag-enc-diag-diag-2");
        }

    }


    @Nested
    class Extract {
        @Test
        void success() throws MustHaveViolatedException {
            Condition condition = new Condition();
            condition.setId("Condition1");
            condition.setSubject(new Reference("Patient1"));
            condition.setAsserter(new Reference("Asserter1"));

            assertThat(referenceExtractor.extract(condition, GROUPS, "Test")).containsExactly(new ReferenceWrapper(ATTRIBUTE_MUST_HAVE, List.of("Patient1"), "Test", "Condition/Condition1"), new ReferenceWrapper(ATTRIBUTE_2, List.of("Asserter1"), "Test", "Condition/Condition1"));
        }

        @Test
        void violated() {
            Condition condition = new Condition();
            condition.setId("Condition1");
            assertThatThrownBy(() -> referenceExtractor.extract(condition, GROUPS, "Test")).isInstanceOf(MustHaveViolatedException.class);
        }

    }

    @Nested
    class collectReferences {
        @Test
        void shouldCollectSingleReference() {
            Reference reference = new Reference("Patient/123");

            List<String> result = referenceExtractor.collectReferences(reference);

            assertThat(result)
                    .containsExactly("Patient/123");
        }

        @Test
        void shouldReturnEmptyListWhenReferenceIsEmpty() {
            Reference reference = new Reference(); // no reference set

            List<String> result = referenceExtractor.collectReferences(reference);

            assertThat(result)
                    .isEmpty();
        }

        @Test
        void shouldReturnEmptyListForPrimitiveElement() {
            StringType primitive = new StringType("hello");

            List<String> result = referenceExtractor.collectReferences(primitive);

            assertThat(result)
                    .isEmpty();
        }

        @Test
        void shouldCollectNestedReferences() {
            // Observation -> subject -> Reference("Patient/123")
            Observation observation = new Observation();
            observation.setSubject(new Reference("Patient/123"));

            // Add another nested reference through performer
            observation.addPerformer(new Reference("Practitioner/456"));

            List<String> result = referenceExtractor.collectReferences(observation);

            assertThat(result)
                    .containsExactlyInAnyOrder("Patient/123", "Practitioner/456");
        }

    }

    @Nested
    class NullSafetyTests {

        @Test
        void collectReferences_withNullReferenceString_shouldNotThrowNPE() {
            // This replicates the exact scenario that caused your crash:
            // An object exists (Reference), but the actual string inside is null.
            Reference refWithNull = new Reference();
            // In some HAPI versions, manually setting null can bypass hasReference() checks
            // depending on how the parser built the object.
            refWithNull.setReference(null);

            List<String> result = referenceExtractor.collectReferences(refWithNull);

            assertThat(result).isEmpty();
        }

        @Test
        void collectReferences_withNullElement_shouldReturnEmptyList() {
            // Tests the guard at the start of the recursive method
            List<String> result = referenceExtractor.collectReferences(null);
            assertThat(result).isEmpty();
        }

        @Test
        void extract_withNullInput_shouldReturnEmptyList() throws MustHaveViolatedException {
            // Tests the top-level guards in the extract method
            assertThat(referenceExtractor.extract(null, GROUPS, "Test")).isEmpty();
            assertThat(referenceExtractor.extract(new Condition(), null, "Test")).isEmpty();
            assertThat(referenceExtractor.extract(new Condition(), GROUPS, null)).isEmpty();
        }

        @Test
        void extract_withMissingGroupId_shouldReturnEmptyList() throws MustHaveViolatedException {
            // Tests the groupMap.get(groupId) guard
            Condition condition = new Condition();
            condition.setId("C1");

            List<ReferenceWrapper> result = referenceExtractor.extract(condition, GROUPS, "NonExistentGroup");

            assertThat(result).isEmpty();
        }

        @Test
        void getReferences_withMalformedResource_shouldHandleNullElementsInStream() throws MustHaveViolatedException {
            // This simulates a FHIR object that might have a null in its children list
            // (rare in HAPI, but possible with custom extensions or malformed parsing)
            Condition condition = new Condition() {
                @Override
                public List<org.hl7.fhir.r4.model.Property> children() {
                    // Force a null property into the stream
                    List<org.hl7.fhir.r4.model.Property> props = new java.util.ArrayList<>(super.children());
                    props.add(null);
                    return props;
                }
            };
            condition.setId("C1");
            condition.setSubject(new Reference("Patient/1"));

            List<String> result = referenceExtractor.getReferences(condition, ATTRIBUTE_OPTIONAL);

            assertThat(result).containsExactly("Patient/1");
        }
    }

    @Nested
    class ExceptionHandlingTests {

        @Test
        void extract_shouldRethrowMustHaveViolatedException_whenWrappedInRuntimeException() throws MustHaveViolatedException {
            // 1. Create a real group with a real attribute
            AnnotatedAttribute attribute = new AnnotatedAttribute("Condition.subject", "Condition.subject", true, List.of("Group"));
            AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Poison", "Condition", "url", List.of(attribute), List.of());
            Map<String, AnnotatedAttributeGroup> localGroups = Map.of("Poison", group);

            // 2. Use Mockito to inject a "poisoned" fhirPathEngine into the extractor
            // We need to use Reflection or a setter if fhirPathEngine is final/private
            // But since we are in the same package, we can just swap it if it's not final,
            // OR use a Mockito Spy on the ReferenceExtractor itself.

            ReferenceExtractor spyExtractor = org.mockito.Mockito.spy(referenceExtractor);

            // Simulate the internal call throwing the wrapped exception
            org.mockito.Mockito.doThrow(new RuntimeException(new MustHaveViolatedException("Simulated violation")))
                    .when(spyExtractor).getReferences(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(attribute));

            // 3. Assert the unwrapping logic works
            assertThatThrownBy(() -> spyExtractor.extract(new Condition(), localGroups, "Poison"))
                    .isExactlyInstanceOf(MustHaveViolatedException.class)
                    .hasMessageContaining("Simulated violation");
        }

        @Test
        void extract_shouldLogErrorAndRethrow_whenGenericRuntimeExceptionOccurs() throws MustHaveViolatedException {
            AnnotatedAttribute attribute = new AnnotatedAttribute("Condition.subject", "Condition.subject", true, List.of("Group"));
            AnnotatedAttributeGroup group = new AnnotatedAttributeGroup("Error", "Condition", "url", List.of(attribute), List.of());
            Map<String, AnnotatedAttributeGroup> localGroups = Map.of("Error", group);

            ReferenceExtractor spyExtractor = org.mockito.Mockito.spy(referenceExtractor);

            // Simulate a generic RuntimeException without a MustHaveViolatedException cause
            org.mockito.Mockito.doThrow(new RuntimeException("Unexpected technical error"))
                    .when(spyExtractor).getReferences(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(attribute));

            assertThatThrownBy(() -> spyExtractor.extract(new Condition(), localGroups, "Error"))
                    .isExactlyInstanceOf(RuntimeException.class)
                    .hasMessage("Unexpected technical error");
        }
    }

}
