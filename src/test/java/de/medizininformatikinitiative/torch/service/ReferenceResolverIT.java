package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.model.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.ResourceBundle;
import de.medizininformatikinitiative.torch.model.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.util.ProfileMustHaveChecker;
import de.medizininformatikinitiative.torch.util.ReferenceExtractor;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
    @Autowired
    private ReferenceExtractor referenceExtractor;

    @Autowired
    private DataStore dataStore;

    @Autowired
    private ProfileMustHaveChecker profileMustHaveChecker;

    @Autowired
    private CompartmentManager compartmentManager;

    @Autowired
    private ConsentHandler consentHandler;

    @Autowired
    FhirContext fhirContext;

    private ReferenceResolver referenceResolver;

    private IParser parser;

    @BeforeAll
    void setUp() {
        Map<String, AnnotatedAttributeGroup> attributeGroupMap = new HashMap<>();

        AnnotatedAttribute patientReference = new AnnotatedAttribute("Patient.id", "Patient.id", "Patient.id", true, List.of("Patient"));
        AnnotatedAttribute conditionId = new AnnotatedAttribute("Condition.id", "Condition.id", "Condition.id", true);
        AnnotatedAttributeGroup conditionGroup = new AnnotatedAttributeGroup("ConditionGroup", "CG12345", "Condition", List.of(conditionId, patientReference), List.of(), false);
        attributeGroupMap.put("Condition", conditionGroup);

        this.referenceResolver = new ReferenceResolver(referenceExtractor, dataStore, profileMustHaveChecker, compartmentManager, consentHandler, attributeGroupMap);
        this.parser = FhirContext.forR4().newJsonParser();
    }

    @Nested
    class noReferences {

        @Test
        void resolveCoreBundle_success() {
            ResourceBundle coreBundle = new ResourceBundle();
            Medication testResource = new Medication();
            testResource.setId("testResource");
            coreBundle.put(new ResourceGroupWrapper(testResource, Set.of()));

            Mono<ResourceBundle> result = referenceResolver.resolveCoreBundle(coreBundle);

            StepVerifier.create(result)
                    .assertNext(bundle -> assertThat(bundle.isEmpty()).isFalse())
                    .verifyComplete();
        }

        @Test
        void resolvePatientBundle_success() {
            PatientResourceBundle patientBundle = new PatientResourceBundle("VHF00006");
            Patient patient = new Patient();
            patient.setId("testPatient");
            patientBundle.put(new ResourceGroupWrapper(patient, Set.of()));
            ResourceBundle coreBundle = new ResourceBundle();
            Boolean applyConsent = true;

            Mono<PatientResourceBundle> result = referenceResolver.resolvePatient(patientBundle, coreBundle, applyConsent);

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
            AnnotatedAttribute patiendID = new AnnotatedAttribute("Patient.id", "Patient.id", "Patient.id", true);
            AnnotatedAttributeGroup patientGroup = new AnnotatedAttributeGroup("Patient1", "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient", List.of(patiendID), List.of());

            AnnotatedAttribute conditionSubject = new AnnotatedAttribute("Condition.subject", "Condition.subject", "Condition.subject", true, List.of("Patient1"));
            AnnotatedAttributeGroup conditionGroup = new AnnotatedAttributeGroup("Condition1", "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose", List.of(conditionSubject), List.of());

            patientBundle.put(new ResourceGroupWrapper(patient, Set.of("Patient1")));
            patientBundle.put(new ResourceGroupWrapper(condition, Set.of("Condition1")));

            Mono<PatientResourceBundle> result = referenceResolver.resolvePatient(patientBundle, coreBundle, false);
            StepVerifier.create(result)
                    .assertNext(bundle -> {
                        System.out.println("Bundle ID " + bundle.patientId() + " " + bundle.keySet());
                        assertThat(bundle.isEmpty()).isFalse();
                    })
                    .verifyComplete();
        }

        @Test
        void resolvePatientBundle_failure() {
            PatientResourceBundle patientBundle = new PatientResourceBundle("VHF00006");
            Patient patient = parser.parseResource(Patient.class, PATIENT);
            ResourceBundle coreBundle = new ResourceBundle();
            Condition condition = parser.parseResource(Condition.class, CONDITION);
            condition.setSubject(new Reference("Patient/123"));
            AnnotatedAttribute patiendID = new AnnotatedAttribute("Patient.id", "Patient.id", "Patient.id", true);
            AnnotatedAttributeGroup patientGroup = new AnnotatedAttributeGroup("Patient1", "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient", List.of(patiendID), List.of());

            AnnotatedAttribute conditionSubject = new AnnotatedAttribute("Condition.subject", "Condition.subject", "Condition.subject", true);
            AnnotatedAttributeGroup conditionGroup = new AnnotatedAttributeGroup("Condition1", "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose", List.of(conditionSubject), List.of());


            Mono<PatientResourceBundle> result = referenceResolver.resolvePatient(patientBundle, coreBundle, false);
            StepVerifier.create(result)
                    .assertNext(bundle -> {
                                System.out.println("Bundle ID " + bundle.patientId() + " " + bundle.keySet());
                                assertThat(bundle.isEmpty()).isFalse();
                                assertThat(bundle.contains("Patient/VHF00006")).isTrue();
                                assertThat(bundle.contains("Condition/2")).isTrue();
                            }
                    )
                    .verifyComplete();
        }


    }

    //TODO Test Server Interact

    //TODO Test Core Bundle
}
