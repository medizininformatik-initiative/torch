package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.util.ProfileMustHaveChecker;
import de.medizininformatikinitiative.torch.util.ReferenceExtractor;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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

    public static final String PAT_REFERENCE = "Patient/VHF00006";

    private ReferenceExtractor extractor;

    @MockBean
    private DataStore dataStore;

    @Autowired
    private ProfileMustHaveChecker profileMustHaveChecker;

    @Autowired
    private CompartmentManager compartmentManager;

    @MockBean
    private ConsentHandler consentHandler;

    @Autowired
    FhirContext fhirContext;

    private ReferenceResolver referenceResolver;

    private IParser parser;

    AnnotatedAttributeGroup patientGroup;

    Map<String, AnnotatedAttributeGroup> attributeGroupMap = new HashMap<>();

    @BeforeAll
    void setUp() {


        AnnotatedAttribute patiendID = new AnnotatedAttribute("Patient.id", "Patient.id", "Patient.id", true);
        AnnotatedAttribute patiendGender = new AnnotatedAttribute("Patient.gender", "Patient.gender", "Patient.gender", true);
        patientGroup = new AnnotatedAttributeGroup("Patient1", "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient", List.of(patiendID, patiendGender), List.of());

        AnnotatedAttribute conditionSubject = new AnnotatedAttribute("Condition.subject", "Condition.subject", "Condition.subject", true, List.of("Patient1"));
        AnnotatedAttributeGroup conditionGroup = new AnnotatedAttributeGroup("Condition1", "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose", List.of(conditionSubject), List.of());


        attributeGroupMap.put("Patient1", patientGroup);
        attributeGroupMap.put("Condition1", conditionGroup);


        this.referenceResolver = new ReferenceResolver(fhirContext, dataStore, profileMustHaveChecker, compartmentManager, consentHandler);
        this.parser = FhirContext.forR4().newJsonParser();
    }

    @Nested
    class noReferences {

        @Test
        void resolveCoreBundle_success() {
            ResourceBundle coreBundle = new ResourceBundle();
            Medication testResource = new Medication();
            testResource.setId("testResource");
            coreBundle.mergingPut(new ResourceGroupWrapper(testResource, Set.of()));

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
            patientBundle.mergingPut(new ResourceGroupWrapper(patient, Set.of()));
            ResourceBundle coreBundle = new ResourceBundle();
            Boolean applyConsent = true;

            Mono<PatientResourceBundle> result = referenceResolver.resolvePatient(patientBundle, coreBundle, applyConsent, attributeGroupMap);

            StepVerifier.create(result)
                    .assertNext(bundle -> assertThat(bundle.isEmpty()).isFalse())
                    .verifyComplete();
        }
    }

    @Nested
    class KnownReferences {
        @Test
        void resolvePatientBundle_success() {
            PatientResourceBundle patientBundle = new PatientResourceBundle("VHF00006");
            Patient patient = parser.parseResource(Patient.class, PATIENT);
            ResourceBundle coreBundle = new ResourceBundle();
            Condition condition = parser.parseResource(Condition.class, CONDITION);


            patientBundle.mergingPut(new ResourceGroupWrapper(patient, Set.of()));
            patientBundle.mergingPut(new ResourceGroupWrapper(condition, Set.of("Condition1")));

            Mono<PatientResourceBundle> result = referenceResolver.resolvePatient(patientBundle, coreBundle, false, attributeGroupMap);
            // Validate the result using StepVerifier
            StepVerifier.create(result)
                    .assertNext(bundle -> {
                        System.out.println("Bundle ID " + bundle.patientId() + " " + bundle.keySet());
                        assertThat(bundle.isEmpty()).isFalse();
                        Mono<ResourceGroupWrapper> resultCondition = bundle.get("Condition/2");

                        StepVerifier.create(resultCondition)
                                .assertNext(wrapper -> {
                                    assertThat(wrapper).isNotNull();
                                    assertThat(wrapper.groupSet()).containsExactly("Condition1");
                                })
                                .verifyComplete();
                        Mono<ResourceGroupWrapper> resultPatient = bundle.get("Patient/VHF00006");

                        StepVerifier.create(resultPatient)
                                .assertNext(wrapper -> {
                                    assertThat(wrapper).isNotNull();
                                    assertThat(wrapper.groupSet()).containsExactly("Patient1");
                                })
                                .verifyComplete();


                    })
                    .verifyComplete();
        }

        @Test
        void resolvePatientBundle_failure() {
            PatientResourceBundle patientBundle = new PatientResourceBundle("VHF00006");
            Patient patient = new Patient();
            patient.setId("VHF00006");
            ResourceBundle coreBundle = new ResourceBundle();
            Condition condition = parser.parseResource(Condition.class, CONDITION);
            patientBundle.mergingPut(new ResourceGroupWrapper(patient, Set.of("Patient1")));
            patientBundle.mergingPut(new ResourceGroupWrapper(condition, Set.of("Condition1")));

            Mono<PatientResourceBundle> result = referenceResolver.resolvePatient(patientBundle, coreBundle, false, attributeGroupMap);
            StepVerifier.create(result)
                    .assertNext(bundle -> {
                                System.out.println("Bundle ID " + bundle.patientId() + " " + bundle.keySet());
                                assertThat(bundle.isEmpty()).isFalse();
                                assertThat(bundle.contains("Condition/2")).isTrue();
                            }
                    )
                    .verifyComplete();
        }


    }

    @Nested
    class ServerInteract {
        @Test
        void resolvePatientBundle_success() {
            // Parse test resources
            Patient patient = parser.parseResource(Patient.class, ReferenceResolverIT.PATIENT);
            Condition condition = parser.parseResource(Condition.class, ReferenceResolverIT.CONDITION);

            // Create patient bundle with condition
            PatientResourceBundle patientBundle = new PatientResourceBundle("VHF00006");

            patientBundle.mergingPut(new ResourceGroupWrapper(condition, Set.of("Condition1")));

            ResourceBundle coreBundle = new ResourceBundle();

            // Mock DataStore to return the expected patient resource
            when(dataStore.fetchDomainResource("Patient/VHF00006")).thenReturn(Mono.just(patient));

            // Mock ConsentHandler to always return true
            when(consentHandler.checkConsent(eq(patient), any(PatientResourceBundle.class)))
                    .thenReturn(true);

            System.out.println("Checker Result" + profileMustHaveChecker.fulfilled(patient, patientGroup));
            // Call method under test
            Mono<PatientResourceBundle> result = referenceResolver.resolvePatient(patientBundle, coreBundle, true, attributeGroupMap);

            // Validate the result using StepVerifier
            StepVerifier.create(result)
                    .assertNext(bundle -> {
                        System.out.println("Bundle ID " + bundle.patientId() + " " + bundle.keySet());
                        assertThat(bundle.isEmpty()).isFalse();
                        Mono<ResourceGroupWrapper> resultCondition = bundle.get("Condition/2");

                        StepVerifier.create(resultCondition)
                                .assertNext(wrapper -> {
                                    assertThat(wrapper).isNotNull();
                                    assertThat(wrapper.groupSet()).containsExactly("Condition1");
                                })
                                .verifyComplete();
                        Mono<ResourceGroupWrapper> resultPatient = bundle.get("Patient/VHF00006");

                        StepVerifier.create(resultPatient)
                                .assertNext(wrapper -> {
                                    assertThat(wrapper).isNotNull();
                                    assertThat(wrapper.groupSet()).containsExactly("Patient1");
                                })
                                .verifyComplete();


                    })
                    .verifyComplete();

            // Verify that the mocked methods were called as expected
            verify(dataStore, times(1)).fetchDomainResource("Patient/VHF00006");
            verify(consentHandler, times(1)).checkConsent(eq(patient), any(PatientResourceBundle.class));
        }
    }

}
