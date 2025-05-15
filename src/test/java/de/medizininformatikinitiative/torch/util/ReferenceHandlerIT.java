package de.medizininformatikinitiative.torch.util;


import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.model.management.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.service.DataStore;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

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
    public static final String REFERENCE_MEDICATION = "Medication/testMedication";

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

        AnnotatedAttribute patientID = new AnnotatedAttribute("Patient.id", "Patient.id", "Patient.id", true);
        AnnotatedAttribute patientGender = new AnnotatedAttribute("Patient.gender", "Patient.gender", "Patient.gender", true);
        patientGroup = new AnnotatedAttributeGroup("Patient1", "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient", List.of(patientID, patientGender), List.of(), null);

        AnnotatedAttribute conditionSubject = new AnnotatedAttribute("Condition.subject", "Condition.subject", "Condition.subject", true, List.of("Patient1"));
        AnnotatedAttributeGroup conditionGroup = new AnnotatedAttributeGroup("Condition1", "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose", List.of(conditionSubject), List.of(), null);

        medicationID = new AnnotatedAttribute("Medication.id", "Medication.id", "Medication.id", true, List.of());
        AnnotatedAttribute medicationAdherence = new AnnotatedAttribute("Medication.adherence", "Medication.adherence", "Medication.adherence", true, List.of());
        AnnotatedAttributeGroup medicationGroup = new AnnotatedAttributeGroup("Medication1", "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/Medication", List.of(medicationID), List.of(), null);
        AnnotatedAttributeGroup medicationGroup2 = new AnnotatedAttributeGroup("Medication2", "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/Medication", List.of(medicationID, medicationAdherence), List.of(), null);

        attributeGroupMap.put("Patient1", patientGroup);
        attributeGroupMap.put("Condition1", conditionGroup);
        attributeGroupMap.put("Medication1", medicationGroup);
        attributeGroupMap.put("Medication2", medicationGroup2);

        organization = new Organization();
        organization.setId("evilInc");


        this.referenceHandler = new ReferenceHandler(profileMustHaveChecker);
        this.parser = FhirContext.forR4().newJsonParser();
    }

    @Nested
    class CoreBundleOnly {

        @Test
        void resolveCoreBundle_success() {
            referenceAttribute = new AnnotatedAttribute("Encounter.evidence", "Encounter.evidence", "Encounter.evidence", true, List.of("Medication1"));
            ResourceBundle coreBundle = new ResourceBundle();
            Medication testResource = parser.parseResource(Medication.class, MEDICATION);
            coreBundle.put(new ResourceGroupWrapper(testResource, Set.of()));
            Flux<List<ResourceGroup>> result = referenceHandler.handleReference(new ReferenceWrapper(referenceAttribute, List.of(REFERENCE_MEDICATION), "EncounterGroup", "parent"), null, coreBundle, attributeGroupMap);

            StepVerifier.create(result)
                    .assertNext(medication -> assertThat(medication.getFirst()).isEqualTo(new ResourceGroup(REFERENCE_MEDICATION, "Medication1")))
                    .verifyComplete();

            // Assuming the method returns a Map<ResourceGroup, Boolean>
            assertThat(coreBundle.resourceGroupValidity())
                    .containsExactly(entry(new ResourceGroup(REFERENCE_MEDICATION, "Medication1"), true));
        }

        @Test
        void resolveCoreBundleFail() {
            referenceAttribute = new AnnotatedAttribute("Encounter.evidence", "Encounter.evidence", "Encounter.evidence", true, List.of("Medication2"));
            ResourceBundle coreBundle = new ResourceBundle();
            Medication testResource = parser.parseResource(Medication.class, MEDICATION);
            coreBundle.put(new ResourceGroupWrapper(testResource, Set.of()));
            Flux<List<ResourceGroup>> result = referenceHandler.handleReference(new ReferenceWrapper(referenceAttribute, List.of(REFERENCE_MEDICATION), "EncounterGroup", "parent"), null, coreBundle, attributeGroupMap);

            StepVerifier.create(result)
                    .expectError()
                    .verify();
        }
    }

    @Nested
    class PatientBundle {

        @Test
        void resolveCoreBundleResource_success() {
            referenceAttribute = new AnnotatedAttribute("Encounter.evidence", "Encounter.evidence", "Encounter.evidence", true, List.of("Medication1"));
            ResourceBundle coreBundle = new ResourceBundle();
            Medication testResource = parser.parseResource(Medication.class, MEDICATION);
            coreBundle.put(new ResourceGroupWrapper(testResource, Set.of()));

            PatientResourceBundle patientBundle = new PatientResourceBundle("VHF00006");
            Patient testPatient = parser.parseResource(Patient.class, PATIENT);
            patientBundle.put(new ResourceGroupWrapper(testPatient, Set.of()));

            Flux<List<ResourceGroup>> result = referenceHandler.handleReference(new ReferenceWrapper(referenceAttribute, List.of(REFERENCE_MEDICATION), "EncounterGroup", "parent"), null, coreBundle, attributeGroupMap);

            StepVerifier.create(result)
                    .assertNext(medication -> assertThat(medication.getFirst()).isEqualTo(new ResourceGroup(REFERENCE_MEDICATION, "Medication1")))
                    .verifyComplete();

            // Assuming the method returns a Map<ResourceGroup, Boolean>
            assertThat(coreBundle.resourceGroupValidity())
                    .containsExactly(entry(new ResourceGroup(REFERENCE_MEDICATION, "Medication1"), true));
        }

        @Test
        void resolveCoreBundleFail() {
            referenceAttribute = new AnnotatedAttribute("Encounter.subject", "Encounter.subject", "Encounter.subject", true, List.of("Patient1"));
            ResourceBundle coreBundle = new ResourceBundle();
            Medication testResource = parser.parseResource(Medication.class, MEDICATION);
            coreBundle.put(new ResourceGroupWrapper(testResource, Set.of()));
            Flux<List<ResourceGroup>> result = referenceHandler.handleReference(new ReferenceWrapper(referenceAttribute, List.of(PAT_REFERENCE), "EncounterGroup", "parent"), null, coreBundle, attributeGroupMap);

            StepVerifier.create(result)
                    .expectError()
                    .verify();
        }
    }


}

