package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import de.medizininformatikinitiative.torch.TargetClassCreationException;
import de.medizininformatikinitiative.torch.Torch;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceAttribute;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Resource;
import org.junit.experimental.runners.Enclosed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Enclosed.class)
@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, classes = Torch.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BatchCopierRedacterIT {

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

    Map<String, AnnotatedAttributeGroup> attributeGroupMap = new HashMap<>();

    @BeforeEach
    void setUp() {


        AnnotatedAttribute patiendID = new AnnotatedAttribute("Patient.id", "Patient.id", "Patient.id", true);
        AnnotatedAttribute patiendGender = new AnnotatedAttribute("Patient.gender", "Patient.gender", "Patient.gender", true);
        patientGroup = new AnnotatedAttributeGroup("Patient1", "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient", List.of(patiendID, patiendGender), List.of());

        conditionSubject = new AnnotatedAttribute("Condition.subject", "Condition.subject", "Condition.subject", true, List.of("Patient1"));
        conditionMeta = new AnnotatedAttribute("Condition.meta", "Condition.meta", "Condition.meta", true, List.of("Patient1"));
        conditionId = new AnnotatedAttribute("Condition.id", "Condition.id", "Condition.id", true, List.of("Patient1"));
        conditionGroup = new AnnotatedAttributeGroup("Condition1", CONDITION_PROFILE, List.of(conditionSubject, conditionMeta, conditionId), List.of());

        expectedAttribute = new ResourceAttribute("Condition/2", conditionSubject);


        attributeGroupMap.put("Patient1", patientGroup);
        attributeGroupMap.put("Condition1", conditionGroup);


        this.batchCopierRedacter = new BatchCopierRedacter(copier, redacter);
        this.parser = FhirContext.forR4().newJsonParser();
    }

    @Nested
    class transformResource {

        @Test
        public void testResourceWithKnownGroups() throws TargetClassCreationException, MustHaveViolatedException {
            Condition condition = parser.parseResource(Condition.class, CONDITION);
            Condition expectedResult = parser.parseResource(Condition.class, CONDITION_RESULT);

            Resource result = batchCopierRedacter.transform(new ExtractionRedactionWrapper(condition, Set.of(CONDITION_PROFILE), Map.of("Condition.subject", Set.of("Patient/VHF00006")), new HashSet<>(conditionGroup.attributes())));

            assertThat(parser.setPrettyPrint(true).encodeResourceToString(result))
                    .isEqualTo(parser.setPrettyPrint(true).encodeResourceToString(expectedResult));
        }


    }

    @Nested
    class transformBundle {
        @Test
        public void testResourceWithKnownGroups() throws TargetClassCreationException, MustHaveViolatedException {
            Condition condition = parser.parseResource(Condition.class, CONDITION);
            Condition expectedResult = parser.parseResource(Condition.class, CONDITION_RESULT);

            PatientResourceBundle bundle = new PatientResourceBundle("Patient1");
            bundle.put(condition, "Condition1", true);

            Mono<PatientResourceBundle> resultMono = batchCopierRedacter.transform(bundle, attributeGroupMap);

            StepVerifier.create(resultMono)
                    .assertNext(resultBundle -> {
                        assertThat(resultBundle.bundle().cache()).hasSize(1);
                        Mono<Resource> actualConditionMono = resultBundle.get("Conditions/2");

                        // Ensure the Mono exists
                        assertThat(actualConditionMono).isNotNull();

                        // Extract the Resource from the Mono before encoding
                        actualConditionMono.doOnNext(actualCondition -> {
                            String actualJson = parser.setPrettyPrint(true).encodeResourceToString(actualCondition);
                            String expectedJson = parser.setPrettyPrint(true).encodeResourceToString(expectedResult);

                            assertThat(actualJson).isEqualTo(expectedJson);
                        }).block(); // Blocking only inside test for assertion
                    })
                    .verifyComplete();
        }


        @Nested
        class transformBatch {
            @Test
            public void testResourceWithKnownGroups() throws TargetClassCreationException, MustHaveViolatedException {
                Condition condition = parser.parseResource(Condition.class, CONDITION);
                Condition expectedResult = parser.parseResource(Condition.class, CONDITION_RESULT);

                PatientResourceBundle bundle = new PatientResourceBundle("Patient1");
                PatientBatchWithConsent consentBatch = PatientBatchWithConsent.fromList(List.of(bundle));
                bundle.put(condition, "Condition1", true);

                Mono<PatientBatchWithConsent> resultMono = batchCopierRedacter.transformBatch(Mono.just(consentBatch), attributeGroupMap);

                StepVerifier.create(resultMono)
                        .assertNext(resultBatch -> {
                            assertThat(resultBatch.bundles()).hasSize(1);
                            PatientResourceBundle resultBundle = resultBatch.bundles().get("Patient1");
                            assertThat(resultBundle.bundle().cache()).hasSize(1);
                            Mono<Resource> actualConditionMono = resultBundle.get("Conditions/2");

                            // Ensure the Mono exists
                            assertThat(actualConditionMono).isNotNull();

                            // Extract the Resource from the Mono before encoding
                            actualConditionMono.doOnNext(actualCondition -> {
                                String actualJson = parser.setPrettyPrint(true).encodeResourceToString(actualCondition);
                                String expectedJson = parser.setPrettyPrint(true).encodeResourceToString(expectedResult);

                                assertThat(actualJson).isEqualTo(expectedJson);
                            }).block(); // Blocking only inside test for assertion
                        })
                        .verifyComplete();
            }


        }
    }

}
