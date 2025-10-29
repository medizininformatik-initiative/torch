package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.torch.model.crtdl.Filter;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.model.management.ResourceGroupWrapper;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReferenceResolverIT {
    private static final String PATIENT = """
            {
                     "resourceType": "Patient",
                     "id": "VHF00006",
                     "meta": {
                       "profile": [
                         "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient"
                       ]
                     },
                     "identifier": [
                       {
                         "use": "usual",
                         "type": {
                           "coding": [
                             {
                               "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
                               "code": "MR"
                             }
                           ]
                         },
                         "system": "https://VHF.de/pid",
                         "value": "VHF00006"
                       }
                     ],
                     "name": [
                       {
                         "use": "official",
                         "family": "DUMMY_SURNAME",
                         "given": [
                           "DUMMY_NAME"
                         ]
                       }
                     ],
                     "gender": "male",
                     "birthDate": "2001-11-01",
                     "address": [
                       {
                         "extension": [
                           {
                             "url": "http://terminology.hl7.org/CodeSystem/data-absent-reason",
                             "valueCode": "unknown"
                           }
                         ]
                       }
                     ]
                   }""";

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

    public static final String DIAG_REFERENCE = "Condition/2";

    public static final String PAT_REFERENCE = "Patient/VHF00006";
    private static final String ENCOUNTER = """
            
              {
                  "resourceType": "Encounter",
                  "id": "VHF00006-E-1",
                  "meta": {
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
                      "_system": {
                        "extension": [
                          {
                            "url": "http://terminology.hl7.org/CodeSystem/data-absent-reason",
                            "valueCode": "unknown"
                          }
                        ]
                      },
                      "value": "VHF00006-E-1",
                      "assigner": {
                        "identifier": {
                          "system": "https://www.medizininformatik-initiative.de/fhir/core/NamingSystem/org-identifier",
                          "value": "VHF"
                        }
                      }
                    }
                  ],
                  "status": "finished",
                  "class": {
                    "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                    "code": "IMP",
                    "display": "inpatient encounter"
                  },
                  "type": [
                    {
                      "coding": [
                        {
                          "code": "einrichtungskontakt",
                          "display": "Einrichtungskontakt"
                        }
                      ]
                    }
                  ],
                  "subject": {
                    "reference": "Patient/VHF00006"
                  },
                  "period": {
                    "start": "2019-01-01T00:00:00+01:00",
                    "end": "2021-01-02T00:00:00+01:00"
                  },
                  "diagnosis": [
                    {
                      "condition": {
                        "reference": "Condition/2"
                      },
                      "use": {
                        "coding": [
                          {
                            "system": "http://terminology.hl7.org/CodeSystem/diagnosis-role",
                            "code": "CC",
                            "display": "Chief complaint"
                          }
                        ]
                      }
                    }
                  ]
                }
            """;

    @Autowired
    ReferenceResolver referenceResolver;

    private IParser parser;

    AnnotatedAttributeGroup patientGroup;
    AnnotatedAttributeGroup conditionGroup;
    AnnotatedAttributeGroup encounterGroup;

    AnnotatedAttribute conditionSubject;
    ResourceAttribute expectedAttribute;
    AnnotatedAttribute encounterDiagnosis;

    @Autowired
    private FilterService filterService;

    @BeforeAll
    void setUp() {


        AnnotatedAttribute patiendID = new AnnotatedAttribute("Patient.id", "Patient.id", true);
        AnnotatedAttribute patiendGender = new AnnotatedAttribute("Patient.gender", "Patient.gender", true);

        var filter = new Filter("date", "birthdate", LocalDate.of(2000, 1, 1), LocalDate.of(2005, 1, 1));
        var compiledFilter = filterService.compileFilter(List.of(filter), "Patient");
        patientGroup = new AnnotatedAttributeGroup("Patient1", "Patient", "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/PatientPseudonymisiert", List.of(patiendID, patiendGender), List.of(), compiledFilter);

        conditionSubject = new AnnotatedAttribute("Condition.subject", "Condition.subject", true, List.of("Patient1"));

        conditionGroup = new AnnotatedAttributeGroup("Condition1", "Condition", "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose", List.of(conditionSubject), List.of(), null);


        encounterDiagnosis = new AnnotatedAttribute("Encounter.diagnosis", "Encounter.diagnosis", true, List.of("Condition1"));

        encounterGroup = new AnnotatedAttributeGroup("Encounter1", "Encounter", "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung", List.of(encounterDiagnosis), List.of(), null);


        expectedAttribute = new ResourceAttribute("Condition/2", conditionSubject);


        this.parser = FhirContext.forR4().newJsonParser();
    }

    @Nested
    class noReferences {
        Map<String, AnnotatedAttributeGroup> attributeGroupMap = new HashMap<>() {{
            put("Patient1", patientGroup);
            put("Condition1", conditionGroup);
            put("Encounter1", encounterGroup);
        }};


        @Test
        void resolveCoreBundle_success() {
            ResourceBundle coreBundle = new ResourceBundle();
            Medication testResource = new Medication();
            testResource.setId("testResource");
            coreBundle.put(new ResourceGroupWrapper(testResource, Set.of()));

            Mono<ResourceBundle> result = referenceResolver.resolveCoreBundle(coreBundle, attributeGroupMap);

            StepVerifier.create(result)
                    .assertNext(bundle -> assertThat(bundle.isEmpty()).isFalse())
                    .verifyComplete();
        }

        @Test
        void resolvePatientBundle_success() {
            PatientResourceBundle patientBundle = new PatientResourceBundle("VHF00006");
            Patient patient = new Patient();
            patient.setId("testPatient");
            patientBundle.put(patient);
            patientBundle.put(new ResourceGroupWrapper(patient, Set.of()));
            ResourceBundle coreBundle = new ResourceBundle();
            boolean applyConsent = true;

            Mono<PatientResourceBundle> result = referenceResolver.resolvePatient(patientBundle, coreBundle, applyConsent, attributeGroupMap);

            StepVerifier.create(result)
                    .assertNext(bundle -> assertThat(bundle.isEmpty()).isFalse())
                    .verifyComplete();
        }
    }

    @Test
    void resolvePatientBundle_failure_unsatisfiedFilter() {
        AnnotatedAttribute patiendID = new AnnotatedAttribute("Patient.id", "Patient.id", true);
        AnnotatedAttribute patiendGender = new AnnotatedAttribute("Patient.gender", "Patient.gender", true);
        var filter = new Filter("date", "birthdate", LocalDate.of(2004, 1, 1), LocalDate.of(2005, 1, 1));
        var compiledFilter = filterService.compileFilter(List.of(filter), "Patient");
        AnnotatedAttributeGroup patientGroupWithUnsatisfiedFilter = new AnnotatedAttributeGroup("Patient1", "Patient", "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient", List.of(patiendID, patiendGender), List.of(), compiledFilter);
        Map<String, AnnotatedAttributeGroup> attributeGroupMap = new HashMap<>() {{
            put("Patient1", patientGroupWithUnsatisfiedFilter);
            put("Condition1", conditionGroup);
        }};

        PatientResourceBundle patientBundle = new PatientResourceBundle("VHF00006");
        Patient patient = parser.parseResource(Patient.class, PATIENT);
        ResourceBundle coreBundle = new ResourceBundle();
        Condition condition = parser.parseResource(Condition.class, CONDITION);


        patientBundle.put(new ResourceGroupWrapper(patient, Set.of()));
        patientBundle.put(new ResourceGroupWrapper(condition, Set.of("Condition1")));

        Mono<PatientResourceBundle> result = referenceResolver.resolvePatient(patientBundle, coreBundle, false, attributeGroupMap);
        // Validate the result using StepVerifier
        StepVerifier.create(result)
                .assertNext(bundle -> {
                            // Check attribute mappings in processingBundle
                            ResourceBundle processingBundle = bundle.bundle();
                            assertThat(bundle.isEmpty()).isFalse();
                            assertThat(processingBundle.resourceGroupValidity()).containsExactlyInAnyOrderEntriesOf(
                                    Map.of(new ResourceGroup("Condition/2", "Condition1"), false,
                                            new ResourceGroup("Patient/VHF00006", "Patient1"), false)
                            );
                            assertThat(processingBundle.resourceAttributeValidity()).containsExactlyInAnyOrderEntriesOf(
                                    Map.of(expectedAttribute, false)
                            );
                        }
                )
                .verifyComplete();
    }

    @Nested
    class KnownReferences {


        @Test
        void processResourceGroups_notFetchingResources() {
            Map<String, AnnotatedAttributeGroup> attributeGroupMap = new HashMap<>() {{
                put("Patient1", patientGroup);
                put("Condition1", conditionGroup);
            }};

            PatientResourceBundle patientBundle = new PatientResourceBundle("VHF00006");
            Patient patient = parser.parseResource(Patient.class, PATIENT);
            ResourceBundle coreBundle = new ResourceBundle();
            Condition condition = parser.parseResource(Condition.class, CONDITION);


            patientBundle.put(new ResourceGroupWrapper(patient, Set.of()));
            patientBundle.put(new ResourceGroupWrapper(condition, Set.of("Condition1")));
            patientBundle.bundle().addResourceGroupValidity(new ResourceGroup("Condition/2", "Condition1"), true);

            var result = referenceResolver.processResourceGroups(patientBundle.getValidResourceGroups(), patientBundle, coreBundle, false, attributeGroupMap);


            StepVerifier.create(result)
                    .assertNext(unprocessedResourceGroups -> assertThat(unprocessedResourceGroups).containsExactly(new ResourceGroup("Patient/VHF00006", "Patient1")))
                    .verifyComplete();
        }

        @Test
        void processResourceGroups_mustHaveViolation() {
            Map<String, AnnotatedAttributeGroup> attributeGroupMap = new HashMap<>() {{
                put("Patient1", patientGroup);
                put("Condition1", conditionGroup);
            }};
            PatientResourceBundle patientBundle = new PatientResourceBundle("VHF00006");
            ResourceBundle coreBundle = new ResourceBundle();
            Patient patient = parser.parseResource(Patient.class, PATIENT);
            Condition condition = parser.parseResource(Condition.class, CONDITION).setSubject(null); // marked as must-have

            patientBundle.put(new ResourceGroupWrapper(patient, Set.of()));
            patientBundle.put(new ResourceGroupWrapper(condition, Set.of("Condition1")));
            patientBundle.bundle().addResourceGroupValidity(new ResourceGroup("Condition/2", "Condition1"), true);


            var result = referenceResolver.processResourceGroups(patientBundle.getValidResourceGroups(), patientBundle, coreBundle, false, attributeGroupMap);


            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        void processResourceGroups_withMissingResource() {
            Map<String, AnnotatedAttributeGroup> attributeGroupMap = new HashMap<>() {{
                put("Patient1", patientGroup);
                put("Condition1", conditionGroup);
            }};
            PatientResourceBundle patientBundle = new PatientResourceBundle("VHF00006");
            ResourceBundle coreBundle = new ResourceBundle();

            patientBundle.put("Condition/2"); // add an empty reference
            patientBundle.bundle().addResourceGroupValidity(new ResourceGroup("Condition/2", "Condition1"), true);


            var result = referenceResolver.processResourceGroups(patientBundle.getValidResourceGroups(), patientBundle, coreBundle, false, attributeGroupMap);


            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        void loadReferences_success() {
            Map<String, AnnotatedAttributeGroup> attributeGroupMap = new HashMap<>() {{
                put("Patient1", patientGroup);
                put("Condition1", conditionGroup);
            }};

            PatientResourceBundle patientBundle = new PatientResourceBundle("VHF00006");
            Patient patient = parser.parseResource(Patient.class, PATIENT);
            ResourceBundle coreBundle = new ResourceBundle();
            Condition condition = parser.parseResource(Condition.class, CONDITION);


            patientBundle.put(new ResourceGroupWrapper(patient, Set.of()));
            patientBundle.put(new ResourceGroupWrapper(condition, Set.of("Condition1")));

            var result = referenceResolver.loadReferencesByResourceGroup(patientBundle.getValidResourceGroups(), patientBundle, coreBundle, attributeGroupMap);

            assertThat(result).containsExactly(Map.entry(new ResourceGroup("Condition/2", "Condition1"), List.of(new ReferenceWrapper(conditionSubject, List.of(PAT_REFERENCE), "Condition1", "Condition/2"))));
        }


        @Test
        void loadReferencesRecursive_success() {
            Map<String, AnnotatedAttributeGroup> attributeGroupMap = new HashMap<>() {{
                put("Patient1", patientGroup);
                put("Condition1", conditionGroup);
                put("Encounter1", encounterGroup);
            }};

            PatientResourceBundle patientBundle = new PatientResourceBundle("VHF00006");
            Patient patient = parser.parseResource(Patient.class, PATIENT);
            ResourceBundle coreBundle = new ResourceBundle();
            Condition condition = parser.parseResource(Condition.class, CONDITION);
            Encounter encounter = parser.parseResource(Encounter.class, ENCOUNTER);


            patientBundle.put(new ResourceGroupWrapper(patient, Set.of()));
            patientBundle.put(new ResourceGroupWrapper(condition, Set.of("Condition1")));
            patientBundle.put(new ResourceGroupWrapper(encounter, Set.of("Encounter1")));

            var result = referenceResolver.loadReferencesByResourceGroup(patientBundle.getValidResourceGroups(), patientBundle, coreBundle, attributeGroupMap);


            assertThat(result).containsExactly(Map.entry(new ResourceGroup("Condition/2", "Condition1"),
                            List.of(new ReferenceWrapper(conditionSubject, List.of(PAT_REFERENCE), "Condition1", "Condition/2"))),
                    Map.entry(new ResourceGroup("Encounter/VHF00006-E-1", "Encounter1"),
                            List.of(new ReferenceWrapper(encounterDiagnosis, List.of(DIAG_REFERENCE), "Encounter1", "Encounter/VHF00006-E-1")))
            );
        }


        @Test
        void resolvePatientBundle_success() {
            Map<String, AnnotatedAttributeGroup> attributeGroupMap = new HashMap<>() {{
                put("Patient1", patientGroup);
                put("Condition1", conditionGroup);
            }};

            PatientResourceBundle patientBundle = new PatientResourceBundle("VHF00006");
            Patient patient = parser.parseResource(Patient.class, PATIENT);
            ResourceBundle coreBundle = new ResourceBundle();
            Condition condition = parser.parseResource(Condition.class, CONDITION);


            patientBundle.put(new ResourceGroupWrapper(patient, Set.of()));
            patientBundle.put(new ResourceGroupWrapper(condition, Set.of("Condition1")));

            Mono<PatientResourceBundle> result = referenceResolver.resolvePatient(patientBundle, coreBundle, false, attributeGroupMap);
            // Validate the result using StepVerifier
            StepVerifier.create(result)
                    .assertNext(bundle -> {

                        assertThat(bundle.isEmpty()).isFalse();

                        // Check attribute mappings in processingBundle
                        ResourceBundle processingBundle = bundle.bundle();


                        assertThat(processingBundle.resourceAttributeToChildResourceGroup())
                                .containsKey(expectedAttribute);
                        assertThat(processingBundle.resourceAttributeToChildResourceGroup().get(expectedAttribute))
                                .contains(new ResourceGroup("Patient/VHF00006", "Patient1"));

                        // Validate child-to-parent attribute mapping
                        assertThat(processingBundle.resourceAttributeToParentResourceGroup())
                                .containsKey(expectedAttribute);
                        assertThat(processingBundle.resourceAttributeToParentResourceGroup().get(expectedAttribute))
                                .contains(new ResourceGroup("Condition/2", "Condition1"));


                        assertThat(processingBundle.resourceGroupValidity()).containsExactlyInAnyOrderEntriesOf(
                                Map.of(new ResourceGroup("Condition/2", "Condition1"), true,
                                        new ResourceGroup("Patient/VHF00006", "Patient1"), true)
                        );
                        assertThat(processingBundle.resourceAttributeValidity()).containsExactlyInAnyOrderEntriesOf(
                                Map.of(expectedAttribute, true)
                        );
                    })
                    .verifyComplete();
        }

        @Test
        void resolvePatientBundle_failure() {
            Map<String, AnnotatedAttributeGroup> attributeGroupMap = new HashMap<>() {{
                put("Patient1", patientGroup);
                put("Condition1", conditionGroup);
            }};
            PatientResourceBundle patientBundle = new PatientResourceBundle("VHF00006");
            Patient patient = new Patient();
            patient.setId("VHF00006");
            ResourceBundle coreBundle = new ResourceBundle();
            Condition condition = parser.parseResource(Condition.class, CONDITION);
            patientBundle.put(new ResourceGroupWrapper(patient, Set.of()));
            patientBundle.put(new ResourceGroupWrapper(condition, Set.of("Condition1")));

            Mono<PatientResourceBundle> result = referenceResolver.resolvePatient(patientBundle, coreBundle, false, attributeGroupMap);
            StepVerifier.create(result)
                    .assertNext(bundle -> {
                                // Check attribute mappings in processingBundle
                                ResourceBundle processingBundle = bundle.bundle();
                                assertThat(bundle.isEmpty()).isFalse();
                                assertThat(processingBundle.resourceGroupValidity()).containsExactlyInAnyOrderEntriesOf(
                                        Map.of(new ResourceGroup("Condition/2", "Condition1"), false,
                                                new ResourceGroup("Patient/VHF00006", "Patient1"), false)
                                );
                                assertThat(processingBundle.resourceAttributeValidity()).containsExactlyInAnyOrderEntriesOf(
                                        Map.of(expectedAttribute, false)
                                );
                            }
                    )
                    .verifyComplete();
        }

        @Test
        void resolvePatientBundle_failure_unsatisfiedFilter() {
            AnnotatedAttribute patiendID = new AnnotatedAttribute("Patient.id", "Patient.id", true);
            AnnotatedAttribute patiendGender = new AnnotatedAttribute("Patient.gender", "Patient.gender", true);
            var filter = new Filter("date", "birthdate", LocalDate.of(2004, 1, 1), LocalDate.of(2005, 1, 1));
            var compiledFilter = filterService.compileFilter(List.of(filter), "Patient");
            AnnotatedAttributeGroup patientGroupWithUnsatisfiedFilter = new AnnotatedAttributeGroup("Patient1", "Patient", "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient", List.of(patiendID, patiendGender), List.of(), compiledFilter);
            Map<String, AnnotatedAttributeGroup> attributeGroupMap = new HashMap<>() {{
                put("Patient1", patientGroupWithUnsatisfiedFilter);
                put("Condition1", conditionGroup);
            }};

            PatientResourceBundle patientBundle = new PatientResourceBundle("VHF00006");
            Patient patient = parser.parseResource(Patient.class, PATIENT);
            ResourceBundle coreBundle = new ResourceBundle();
            Condition condition = parser.parseResource(Condition.class, CONDITION);


            patientBundle.put(new ResourceGroupWrapper(patient, Set.of()));
            patientBundle.put(new ResourceGroupWrapper(condition, Set.of("Condition1")));

            Mono<PatientResourceBundle> result = referenceResolver.resolvePatient(patientBundle, coreBundle, false, attributeGroupMap);
            // Validate the result using StepVerifier
            StepVerifier.create(result)
                    .assertNext(bundle -> {
                                // Check attribute mappings in processingBundle
                                ResourceBundle processingBundle = bundle.bundle();
                                assertThat(bundle.isEmpty()).isFalse();
                                assertThat(processingBundle.resourceGroupValidity()).containsExactlyInAnyOrderEntriesOf(
                                        Map.of(new ResourceGroup("Condition/2", "Condition1"), false,
                                                new ResourceGroup("Patient/VHF00006", "Patient1"), false)
                                );
                                assertThat(processingBundle.resourceAttributeValidity()).containsExactlyInAnyOrderEntriesOf(
                                        Map.of(expectedAttribute, false)
                                );
                            }
                    )
                    .verifyComplete();
        }


    }
}
