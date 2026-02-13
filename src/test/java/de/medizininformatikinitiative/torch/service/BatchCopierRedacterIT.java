package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.torch.TargetClassCreationException;
import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.exceptions.RedactionException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionPatientBatch;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Resource;
import org.junit.experimental.runners.Enclosed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Enclosed.class)
@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BatchCopierRedacterIT {


    private static final String CONDITION = """
            {
                           "resourceType": "Condition",
                           "id": "2",
                           "meta": {
                             "profile": [
                               "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose"
                             ]
                           },
                           "clinicalStatus": {
                             "coding": [
                               {
                                 "system": "http://terminology.hl7.org/CodeSystem/condition-clinical",
                                 "code": "active"
                               }
                             ]
                           },
                           "verificationStatus": {
                             "coding": [
                               {
                                 "system": "http://terminology.hl7.org/CodeSystem/condition-ver-status",
                                 "code": "confirmed"
                               }
                             ]
                           },
                           "category": [
                             {
                               "coding": [
                                 {
                                   "system": "http://terminology.hl7.org/CodeSystem/condition-category",
                                   "code": "encounter-diagnosis"
                                 }
                               ]
                             }
                           ],
                           "code": {
                             "coding": [
                               {
                                 "system": "http://snomed.info/sct",
                                 "code": "123456",
                                 "display": "Example diagnosis"
                               }
                             ]
                           },
                           "subject": {
                             "reference": "Patient/VHF00006"
                           },
                           "onsetDateTime": "2023-06-01T00:00:00Z"
                         }""";


    String CONDITION_RESULT = """
              {
              "resourceType": "Condition",
              "id": "2",
              "meta": {
                "profile": [ "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose" ]
              },
              "code": {
                "extension": [ {
                  "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                  "valueCode": "masked"
                } ]
              },
              "subject": {
                "reference": "Patient/VHF00006"
              },
              "_recordedDate": {
                "extension": [ {
                  "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                  "valueCode": "masked"
                } ]
              }
            }""";

    String CONDITION_RESULT_WITHOUT_REFERENCE = """
              {
              "resourceType": "Condition",
              "id": "2",
              "meta": {
                "profile": [ "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose" ]
              },
              "code": {
                "extension": [ {
                  "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                  "valueCode": "masked"
                } ]
              },
              "subject": {
                "_reference": {
                  "extension": [ {
                    "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                    "valueCode": "masked"
                  } ]
                }
              },
              "_recordedDate": {
                "extension": [ {
                  "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                  "valueCode": "masked"
                } ]
              }
            }""";


    String ENCOUNTER = """
                {
                "resourceType": "Encounter",
                "id": "encounter1",
                "meta": {
                    "versionId": "32",
                    "lastUpdated": "2026-02-06T19:22:28.577Z",
                    "profile": [
                        "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung"
                    ],
                    "tag": [
                        {
                            "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationValue",
                            "code": "SUBSETTED"
                        }
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
                        "value": "MII_0000003"
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
                    "reference": "Patient/mii-exa-test-data-patient-3"
                },
                "diagnosis": [
                    {
                        "condition": {
                            "reference": "Condition/mii-exa-test-data-patient-3-diagnose-1"
                        }
                    }
                ]
            }
            """;

    String ENCOUNTER_RESULT = """
              {
              "resourceType": "Encounter",
              "id": "encounter1",
              "meta": {
                "versionId": "32",
                "lastUpdated": "2026-02-06T19:22:28.577Z",
                "profile": [ "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung" ],
                "tag": [ {
                  "system": "http://terminology.hl7.org/CodeSystem/v3-ObservationValue",
                  "code": "SUBSETTED"
                } ]
              },
              "_status": {
                "extension": [ {
                  "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                  "valueCode": "masked"
                } ]
              },
              "class": {
                "extension": [ {
                  "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                  "valueCode": "masked"
                } ]
              },
              "type": [ {
                "coding": [ {
                  "system": "http://fhir.de/CodeSystem/Kontaktebene",
                  "code": "einrichtungskontakt"
                } ]
              } ],
              "subject": {
                "_reference": {
                  "extension": [ {
                    "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                    "valueCode": "masked"
                  } ]
                }
              }
            }""";


    String CONDITION_PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose";

    @Autowired
    ElementCopier copier;


    @Autowired
    Redaction redacter;

    private BatchCopierRedacter batchCopierRedacter;

    private IParser parser;

    AnnotatedAttributeGroup patientGroup;

    ResourceAttribute expectedAttribute;
    AnnotatedAttribute conditionSubject;
    AnnotatedAttribute conditionMeta;
    AnnotatedAttribute conditionId;
    AnnotatedAttributeGroup conditionGroup;
    ResourceGroup validResourceGroup;
    AnnotatedAttributeGroup encounterGroup;

    Map<String, AnnotatedAttributeGroup> attributeGroupMap = new HashMap<>();

    @BeforeEach
    void setUp() {


        AnnotatedAttribute patiendID = new AnnotatedAttribute("Patient.id", "Patient.id", true);
        AnnotatedAttribute patiendGender = new AnnotatedAttribute("Patient.gender", "Patient.gender", true);
        patientGroup = new AnnotatedAttributeGroup("Patient1", "Patient", "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient", List.of(patiendID, patiendGender), List.of());

        conditionSubject = new AnnotatedAttribute("Condition.subject", "Condition.subject", true, List.of("Patient1"));
        conditionMeta = new AnnotatedAttribute("Condition.meta", "Condition.meta", true, List.of("Patient1"));
        conditionId = new AnnotatedAttribute("Condition.id", "Condition.id", true, List.of("Patient1"));
        conditionGroup = new AnnotatedAttributeGroup("Condition1", "Condition", CONDITION_PROFILE, List.of(conditionSubject, conditionMeta, conditionId), List.of());

        AnnotatedAttribute encounterSubject = new AnnotatedAttribute("Encounter.subject", "Encounter.subject", true, List.of("Patient1"));
        AnnotatedAttribute encounterMeta = new AnnotatedAttribute("Encounter.meta", "Encounter.meta", true, List.of("Patient1"));
        AnnotatedAttribute encounterId = new AnnotatedAttribute("Encounter.id", "Encounter.id", true, List.of("Patient1"));
        AnnotatedAttribute encounterType = new AnnotatedAttribute("Encounter.type:Kontaktebene", "Encounter.type.where($this.coding.system='http://fhir.de/CodeSystem/Kontaktebene')", true, List.of("Patient1"));

        encounterGroup = new AnnotatedAttributeGroup("Encounter1", "Encounter", "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung", List.of(encounterSubject, encounterMeta, encounterId, encounterType), List.of());
        expectedAttribute = new ResourceAttribute("Condition/2", conditionSubject);
        validResourceGroup = new ResourceGroup("Patient/VHF00006", "Patient1");


        attributeGroupMap.put("Patient1", patientGroup);
        attributeGroupMap.put("Condition1", conditionGroup);
        attributeGroupMap.put("Encounter1", encounterGroup);


        this.batchCopierRedacter = new BatchCopierRedacter(copier, redacter);
        this.parser = FhirContext.forR4().newJsonParser();
    }


    @Nested
    class transformResource {

        @Test
        void testResourceWithKnownGroups() throws TargetClassCreationException, ReflectiveOperationException, RedactionException {
            Condition condition = parser.parseResource(Condition.class, CONDITION);
            Condition expectedResult = parser.parseResource(Condition.class, CONDITION_RESULT);
            Resource result = batchCopierRedacter.transformResource(new ExtractionRedactionWrapper(condition, Set.of(CONDITION_PROFILE), Map.of("Condition.subject", Set.of("Patient/VHF00006")), conditionGroup.copyTree().get()));
            assertThat(parser.setPrettyPrint(true).encodeResourceToString(result)).isEqualTo(parser.setPrettyPrint(true).encodeResourceToString(expectedResult));
        }
    }

    @Nested
    class transformBundle {
        @Test
        void testResourceWithKnownGroups() {
            Condition condition = parser.parseResource(Condition.class, CONDITION);
            Condition expectedResult = parser.parseResource(Condition.class, CONDITION_RESULT_WITHOUT_REFERENCE);

            PatientResourceBundle bundle = new PatientResourceBundle("PatientBundle");
            bundle.put(condition, "Condition1", true);


            ExtractionResourceBundle result = batchCopierRedacter.transformBundle(ExtractionResourceBundle.of(bundle), attributeGroupMap);

            assertThat(result.cache()).hasSize(1);
            String actualJson = parser.setPrettyPrint(true).encodeResourceToString(result.get("Condition/2").get());
            String expectedJson = parser.setPrettyPrint(true).encodeResourceToString(expectedResult);


            assertThat(actualJson).isEqualTo(expectedJson);

        }

        @Test
        void testEncounter() {
            Encounter encounter = parser.parseResource(Encounter.class, ENCOUNTER);
            Encounter expectedResult = parser.parseResource(Encounter.class, ENCOUNTER_RESULT);


            PatientResourceBundle bundle = new PatientResourceBundle("PatientBundle");
            bundle.put(encounter, "Encounter1", true);
            bundle.bundle().setResourceAttributeValid(expectedAttribute);
            bundle.bundle().addAttributeToChild(expectedAttribute, validResourceGroup);
            bundle.bundle().addResourceGroupValidity(validResourceGroup, true);
            System.out.println(encounterGroup.copyTree().get());
            ExtractionResourceBundle result = batchCopierRedacter.transformBundle(ExtractionResourceBundle.of(bundle), attributeGroupMap);

            assertThat(result.cache()).hasSize(1);
            String actualJson = parser.setPrettyPrint(true).encodeResourceToString(result.get("Encounter/encounter1").get());
            String expectedJson = parser.setPrettyPrint(true).encodeResourceToString(expectedResult);

            assertThat(actualJson).isEqualTo(expectedJson);

        }

        @Test
        void testResourceWithReference() {
            Condition condition = parser.parseResource(Condition.class, CONDITION);
            Condition expectedResult = parser.parseResource(Condition.class, CONDITION_RESULT);


            PatientResourceBundle bundle = new PatientResourceBundle("PatientBundle");
            bundle.put(condition, "Condition1", true);
            bundle.bundle().setResourceAttributeValid(expectedAttribute);
            bundle.bundle().addAttributeToChild(expectedAttribute, validResourceGroup);
            bundle.bundle().addResourceGroupValidity(validResourceGroup, true);

            ExtractionResourceBundle result = batchCopierRedacter.transformBundle(ExtractionResourceBundle.of(bundle), attributeGroupMap);

            assertThat(result.cache()).hasSize(1);
            String actualJson = parser.setPrettyPrint(true).encodeResourceToString(result.get("Condition/2").get());
            String expectedJson = parser.setPrettyPrint(true).encodeResourceToString(expectedResult);

            assertThat(actualJson).isEqualTo(expectedJson);

        }


        @Nested
        class transformBatch {
            @Test
            void testResourceWithKnownGroups() {
                Condition condition = parser.parseResource(Condition.class, CONDITION);
                Condition expectedResult = parser.parseResource(Condition.class, CONDITION_RESULT_WITHOUT_REFERENCE);

                PatientResourceBundle bundle = new PatientResourceBundle("PatientBundle");
                PatientBatchWithConsent consentBatch = PatientBatchWithConsent.fromList(List.of(bundle));
                bundle.put(condition, "Condition1", true);

                ExtractionPatientBatch result = batchCopierRedacter.transformBatch(ExtractionPatientBatch.of(consentBatch), attributeGroupMap);

                assertThat(result.bundles()).hasSize(1);
                ExtractionResourceBundle resultBundle = result.get("PatientBundle");

                assertThat(resultBundle.cache()).hasSize(1);
                String actualJson = parser.setPrettyPrint(true).encodeResourceToString(resultBundle.get("Condition/2").get());
                String expectedJson = parser.setPrettyPrint(true).encodeResourceToString(expectedResult);


                assertThat(actualJson).isEqualTo(expectedJson);

            }


        }
    }

}
