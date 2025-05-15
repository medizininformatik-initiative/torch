package de.medizininformatikinitiative.torch.util;


import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.model.management.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Organization;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReferenceHandlerIT {
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
                           "id": "testCondition",
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

    private static final String MEDICATION = """
            {"resourceType": "Medication",
                           "id": "Medication/testMedication",
                           "meta": {
                             "profile": [
                               "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/Medication"
                             ]
                           }
             }""";

    public static final String PAT_REFERENCE = "Patient/VHF00006";

    @MockBean
    private DataStore dataStore;

    @Autowired
    private ProfileMustHaveChecker profileMustHaveChecker;

    @Autowired
    private CompartmentManager compartmentManager;

    @MockBean
    private ConsentValidator consentValidator;


    private ReferenceHandler referenceHandler;

    private IParser parser;

    AnnotatedAttributeGroup patientGroup;
    private Organization organization;
    private AnnotatedAttribute medicationID;
    private AnnotatedAttribute referenceAttribute;
    Map<String, AnnotatedAttributeGroup> attributeGroupMap = new HashMap<>();

    @BeforeAll
    void setUp() {


        AnnotatedAttribute patiendID = new AnnotatedAttribute("Patient.id", "Patient.id", "Patient.id", true);
        AnnotatedAttribute patiendGender = new AnnotatedAttribute("Patient.gender", "Patient.gender", "Patient.gender", true);
        patientGroup = new AnnotatedAttributeGroup("Patient1", "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient", List.of(patiendID, patiendGender), List.of(), null);

        AnnotatedAttribute conditionSubject = new AnnotatedAttribute("Condition.subject", "Condition.subject", "Condition.subject", true, List.of("Patient1"));
        AnnotatedAttributeGroup conditionGroup = new AnnotatedAttributeGroup("Condition1", "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose", List.of(conditionSubject), List.of(), null);

        medicationID = new AnnotatedAttribute("Medication.id", "Medication.id", "Medication.id", true, List.of());
        AnnotatedAttributeGroup medicationGroup = new AnnotatedAttributeGroup("Medication1", "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/Medication", List.of(medicationID), List.of(), null);


        referenceAttribute = new AnnotatedAttribute("Encounter.evidence", "Encounter.evidence", "Encounter.evidence", true, List.of("Medication1"));

        attributeGroupMap.put("Patient1", patientGroup);
        attributeGroupMap.put("Condition1", conditionGroup);
        attributeGroupMap.put("Medication1", medicationGroup);

        organization = new Organization();
        organization.setId("evilInc");


        this.referenceHandler = new ReferenceHandler(dataStore, profileMustHaveChecker, compartmentManager, consentValidator);
        this.parser = FhirContext.forR4().newJsonParser();
    }

    @Nested
    class coreBundle {

        @Test
        void resolveCoreBundle_success() {
            ResourceBundle coreBundle = new ResourceBundle();
            Medication testResource = parser.parseResource(Medication.class, MEDICATION);
            coreBundle.put(new ResourceGroupWrapper(testResource, Set.of()));
            Flux<List<ResourceGroup>> result = referenceHandler.handleReference(new ReferenceWrapper(referenceAttribute, List.of("Medication/testMedication"), "EncounterGroup", "parent"), null, coreBundle, false, attributeGroupMap);

            StepVerifier.create(result)
                    .assertNext(medication -> assertThat(medication.getFirst()).isEqualTo(new ResourceGroup("Medication/testMedication", "Medication1")))
                    .verifyComplete();

            // Assuming the method returns a Map<ResourceGroup, Boolean>
            assertThat(coreBundle.resourceGroupValidity())
                    .containsExactly(entry(new ResourceGroup("Medication/testMedication", "Medication1"), true));
        }

        @Test
        void resolveCoreBundle_fail_MustHave() {
            // Arrange: Mock the behavior of DataStore to return a 404 error (resource not found)
            String invalidReference = "Medication/invalidReference";
            Mockito.when(dataStore.fetchResourceByReference(invalidReference))
                    .thenReturn(Mono.error(new ResourceNotFoundException("Resource not found for reference: " + invalidReference)));


            ResourceBundle coreBundle = new ResourceBundle();

            Flux<List<ResourceGroup>> result = referenceHandler.handleReference(
                    new ReferenceWrapper(referenceAttribute, List.of(invalidReference), "EncounterGroup", "parent"),
                    null,
                    coreBundle,
                    false,
                    attributeGroupMap
            );

            // Act & Assert: Expect the MustHaveViolatedException to be thrown
            StepVerifier.create(result)
                    .expectError(MustHaveViolatedException.class)  // Expect the MustHaveViolatedException to be thrown
                    .verify();

            assertThat(coreBundle.resourceGroupValidity())
                    .containsExactly(entry(new ResourceGroup("Medication/invalidReference", "Medication1"), false));


        }

        @Test
        void resolveCoreBundle_fail() {
            referenceAttribute = new AnnotatedAttribute("Encounter.evidence", "Encounter.evidence", "Encounter.evidence", false, List.of("Medication1"));


            // Arrange: Mock the behavior of DataStore to return a 404 error (resource not found)
            String invalidReference = "Medication/invalidReference";
            Mockito.when(dataStore.fetchResourceByReference(invalidReference))
                    .thenReturn(Mono.error(new ResourceNotFoundException("Resource not found for reference: " + invalidReference)));


            ResourceBundle coreBundle = new ResourceBundle();

            Flux<List<ResourceGroup>> result = referenceHandler.handleReference(
                    new ReferenceWrapper(referenceAttribute, List.of(invalidReference), "EncounterGroup", "parent"),
                    null,
                    coreBundle,
                    false,
                    attributeGroupMap
            );

            // Act & Assert: Expect an empty list to be returned
            StepVerifier.create(result)
                    .expectNext(Collections.emptyList())  // Expect an empty list to be returned
                    .verifyComplete();  // Complete the sequence after returning the empty list

            assertThat(coreBundle.resourceGroupValidity())
                    .containsExactly(entry(new ResourceGroup("Medication/invalidReference", "Medication1"), false));

        }

    }

}

