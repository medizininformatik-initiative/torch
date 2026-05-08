package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.fhir.QueryParams;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.util.ProfileMustHaveChecker;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.stringValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectResourceLoaderTest {


    @Mock
    DataStore dataStore;
    @Mock
    StructureDefinitionHandler structureDefinitionHandler;
    @Mock
    ProfileMustHaveChecker profileMustHaveChecker;
    @InjectMocks
    DirectResourceLoader directResourceLoader;

    @Nested
    class ProcessCoreAttributeGroupCoverage {

        @Test
        void shouldPutValidTrue_andComplete_whenAtLeastOneResourceIsValid() {
            // mustHave=true => atLeastOneResource starts false
            var attr = new AnnotatedAttribute("Observation.subject", "Observation.subject", true);
            var groupRef = "http://example.org/StructureDefinition/test";
            var group = new AnnotatedAttributeGroup(
                    "core", "Observation", groupRef, List.of(attr), List.of()
            );

            // queries(...) with empty filters => single query with only _profile:below
            Query expectedQuery = Query.of("Observation",
                    QueryParams.EMPTY.appendParam("_profile:below", stringValue(groupRef)));

            Observation obs = new Observation();
            obs.setId("Observation/xyz");

            when(dataStore.search(eq(expectedQuery), eq(DomainResource.class)))
                    .thenReturn(Flux.just(obs));
            when(profileMustHaveChecker.fulfilled(eq(obs), eq(group)))
                    .thenReturn(true);

            ResourceBundle bundle = mock(ResourceBundle.class);

            StepVerifier.create(directResourceLoader.processCoreAttributeGroup(group, bundle))
                    .verifyComplete();

            verify(bundle).put(obs, "core", true);
        }

        @Test
        void shouldPutValidFalse_andThenError_whenMustHaveAndNoValidResource() {
            var attr = new AnnotatedAttribute("Observation.subject", "Observation.subject", true);
            var groupRef = "http://example.org/StructureDefinition/test";
            var group = new AnnotatedAttributeGroup(
                    "core", "Observation", groupRef, List.of(attr), List.of()
            );

            Query expectedQuery = Query.of("Observation",
                    QueryParams.EMPTY.appendParam("_profile:below", stringValue(groupRef)));

            Observation obs = new Observation();
            obs.setId("Observation/xyz");

            when(dataStore.search(eq(expectedQuery), eq(DomainResource.class)))
                    .thenReturn(Flux.just(obs));
            when(profileMustHaveChecker.fulfilled(eq(obs), eq(group)))
                    .thenReturn(false);

            ResourceBundle bundle = mock(ResourceBundle.class);

            StepVerifier.create(directResourceLoader.processCoreAttributeGroup(group, bundle))
                    .expectError(MustHaveViolatedException.class)
                    .verify();
            verify(bundle).put(obs, "core", false);
        }
    }


    @Nested
    class ProcessPatientAttributeGroups {

        @Test
        void testIgnoresEmptyFlux() {

            var attribute = new AnnotatedAttribute("Observation.name", "Observation.name", false);
            var attributeGroup = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(attribute), List.of());
            var patientBundle = new PatientResourceBundle("1");
            var batchWithConsent = PatientBatchWithConsent.fromList(List.of(patientBundle));
            var safeSet = new HashSet<>(List.of("1"));

            when(dataStore.search(any(), any())).thenAnswer(invocation -> Flux.empty());

            var result = directResourceLoader.processPatientAttributeGroups(
                    List.of(attributeGroup),
                    batchWithConsent,
                    safeSet
            );

            StepVerifier.create(result)
                    .assertNext(res -> {
                        assertThat(res.bundles()).containsKey("1");
                        assertThat(res.get("1").bundle().cache()).doesNotContainKey(ExtractionId.fromRelativeUrl("Observation/xyz"));
                    })
                    .verifyComplete();
        }

        @Test
        void testIgnoresEmptyResource() {

            var attribute = new AnnotatedAttribute("Observation.name", "Observation.name", false);
            var attributeGroup = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(attribute), List.of());
            var patientBundle = new PatientResourceBundle("1");
            var batchWithConsent = PatientBatchWithConsent.fromList(List.of(patientBundle));
            var safeSet = new HashSet<>(List.of("1"));

            when(dataStore.search(any(), any())).thenAnswer(invocation -> Flux.just(new Observation()));

            var result = directResourceLoader.processPatientAttributeGroups(
                    List.of(attributeGroup),
                    batchWithConsent,
                    safeSet
            );

            StepVerifier.create(result)
                    .assertNext(res -> {
                        assertThat(res.bundles()).containsKey("1");
                        assertThat(res.get("1").bundle().cache()).doesNotContainKey(ExtractionId.fromRelativeUrl("Observation/xyz"));
                    })
                    .verifyComplete();
        }


        @Test
        void testIgnoresObservationOfUnknownPatient() {

            var attribute = new AnnotatedAttribute("Observation.name", "Observation.name", false);
            var attributeGroup = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(attribute), List.of());

            var patientBundle = new PatientResourceBundle("1");
            var batchWithConsent = PatientBatchWithConsent.fromList(List.of(patientBundle));
            var safeSet = new HashSet<>(List.of("1"));

            Observation observation = new Observation();
            observation.setId("Observation/xyz");
            observation.setSubject(new Reference("Patient/2"));

            when(dataStore.search(any(), any())).thenAnswer(invocation -> Flux.just(observation));

            var result = directResourceLoader.processPatientAttributeGroups(
                    List.of(attributeGroup),
                    batchWithConsent,
                    safeSet
            );

            StepVerifier.create(result)
                    .assertNext(res -> {
                        assertThat(res.bundles()).containsKey("1");
                        assertThat(res.get("1").bundle().cache()).doesNotContainKey(ExtractionId.fromRelativeUrl("Observation/xyz"));
                    })
                    .verifyComplete();
        }

        @Test
        void testIgnoresResourceWithoutPatientReference() {
            // Arrange
            var attribute = new AnnotatedAttribute("Observation.name", "Observation.name", false);
            var attributeGroup = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(attribute), List.of());
            var patientBundle = new PatientResourceBundle("1");
            var batchWithConsent = PatientBatchWithConsent.fromList(List.of(patientBundle));
            var safeSet = new HashSet<>(List.of("1"));

            Observation observation = new Observation();
            observation.setId("xyz");

            when(dataStore.search(any(), any())).thenAnswer(invocation -> Flux.just(observation));

            var result = directResourceLoader.processPatientAttributeGroups(
                    List.of(attributeGroup),
                    batchWithConsent,
                    safeSet
            );

            StepVerifier.create(result)
                    .assertNext(res -> {
                        assertThat(res.bundles()).containsKey("1");
                        assertThat(res.get("1").bundle().cache()).doesNotContainKey(ExtractionId.fromRelativeUrl("Observation/xyz"));
                    })
                    .verifyComplete();
        }


        @Test
        void testStoresObservationWithInvalidMustHave() {
            // Arrange
            var attribute = new AnnotatedAttribute("Observation.name", "Observation.name", false);
            var attributeGroup = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(attribute), List.of());
            var patientBundle = new PatientResourceBundle("1");
            var batchWithConsent = PatientBatchWithConsent.fromList(List.of(patientBundle));
            var safeSet = new HashSet<>(List.of("1"));

            Observation observation = new Observation();
            observation.setId("xyz");
            observation.setSubject(new Reference("Patient/1"));

            when(dataStore.search(any(), any())).thenAnswer(invocation -> Flux.just(observation));
            when(profileMustHaveChecker.fulfilled(observation, attributeGroup)).thenReturn(false);

            var result = directResourceLoader.processPatientAttributeGroups(
                    List.of(attributeGroup),
                    batchWithConsent,
                    safeSet
            );

            StepVerifier.create(result)
                    .assertNext(processedBatch -> {
                        assertThat(processedBatch).isNotNull();
                        assertThat(processedBatch.patientBatch().ids()).containsExactly("1");

                        // Example check for modified bundle cache
                        assertThat(processedBatch.get("1").bundle().cache())
                                .isEqualTo(Map.of(ExtractionId.fromRelativeUrl("Observation/xyz"), Optional.of(observation)));
                        assertThat(processedBatch.get("1").bundle().getValidResourceGroups()).doesNotContain(
                                new ResourceGroup(ExtractionId.fromRelativeUrl("Observation/xyz"), "test"));
                    })
                    .verifyComplete();
        }


        @Test
        void enrichesPatientMetaProfileWhenMissing() {
            var groupRef = "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/PatientPseudonymisiert";
            var patientGroup = new AnnotatedAttributeGroup("patGroup", "Patient", groupRef,
                    List.of(new AnnotatedAttribute("Patient.name", "Patient.name", false)), List.of());

            Patient patient = new Patient();
            patient.setId("1");

            var patientBundle = new PatientResourceBundle("1");
            var batchWithConsent = PatientBatchWithConsent.fromList(List.of(patientBundle));
            var safeSet = new HashSet<>(List.of("1"));

            when(dataStore.search(any(), any())).thenReturn(Flux.just(patient));
            when(profileMustHaveChecker.fulfilled(patient, patientGroup)).thenReturn(true);

            directResourceLoader.processPatientAttributeGroups(List.of(patientGroup), batchWithConsent, safeSet).block();

            assertThat(patient.getMeta().getProfile()).hasSize(1);
            assertThat(patient.getMeta().getProfile().getFirst().getValue()).isEqualTo(groupRef);
        }

        @Test
        void replacesExistingPatientMetaProfileWithTarget() {
            var groupRef = "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/PatientPseudonymisiert";
            var existingProfile = "https://example.org/existing-profile";
            var patientGroup = new AnnotatedAttributeGroup("patGroup", "Patient", groupRef,
                    List.of(new AnnotatedAttribute("Patient.name", "Patient.name", false)), List.of());

            Patient patient = new Patient();
            patient.setId("1");
            patient.getMeta().addProfile(existingProfile);

            var patientBundle = new PatientResourceBundle("1");
            var batchWithConsent = PatientBatchWithConsent.fromList(List.of(patientBundle));
            var safeSet = new HashSet<>(List.of("1"));

            when(dataStore.search(any(), any())).thenReturn(Flux.just(patient));
            when(profileMustHaveChecker.fulfilled(patient, patientGroup)).thenReturn(true);

            directResourceLoader.processPatientAttributeGroups(List.of(patientGroup), batchWithConsent, safeSet).block();

            assertThat(patient.getMeta().getProfile()).hasSize(1);
            assertThat(patient.getMeta().getProfile().getFirst().getValue()).isEqualTo(groupRef);
        }

        @Test
        void doesNotEnrichMetaProfileForNonPatientResources() {
            var groupRef = "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab";
            var observationGroup = new AnnotatedAttributeGroup("obsGroup", "Observation", groupRef,
                    List.of(new AnnotatedAttribute("Observation.code", "Observation.code", false)), List.of());

            Observation observation = new Observation();
            observation.setId("obs1");
            observation.setSubject(new Reference("Patient/1"));

            var patientBundle = new PatientResourceBundle("1");
            var batchWithConsent = PatientBatchWithConsent.fromList(List.of(patientBundle));
            var safeSet = new HashSet<>(List.of("1"));

            when(dataStore.search(any(), any())).thenReturn(Flux.just(observation));
            when(profileMustHaveChecker.fulfilled(observation, observationGroup)).thenReturn(true);

            directResourceLoader.processPatientAttributeGroups(List.of(observationGroup), batchWithConsent, safeSet).block();

            assertThat(observation.getMeta().getProfile()).isEmpty();
        }

        @Test
        void testStoresObservationWithKnownPatient() {
            // Arrange
            var attribute = new AnnotatedAttribute("Observation.name", "Observation.name", false);
            var attributeGroup = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(attribute), List.of());
            var patientBundle = new PatientResourceBundle("1");
            var batchWithConsent = PatientBatchWithConsent.fromList(List.of(patientBundle));
            var safeSet = new HashSet<>(List.of("1"));

            Observation observation = new Observation();
            observation.setId("xyz");
            observation.setSubject(new Reference("Patient/1"));

            when(dataStore.search(any(), any())).thenAnswer(invocation -> Flux.just(observation));
            when(profileMustHaveChecker.fulfilled(observation, attributeGroup)).thenReturn(true);

            var result = directResourceLoader.processPatientAttributeGroups(
                    List.of(attributeGroup),
                    batchWithConsent,
                    safeSet
            );

            StepVerifier.create(result)
                    .assertNext(processedBatch -> {
                        assertThat(processedBatch).isNotNull();
                        assertThat(processedBatch.patientBatch().ids()).containsExactly("1");

                        // Example check for modified bundle cache
                        assertThat(processedBatch.get("1").bundle().cache())
                                .isEqualTo(Map.of(ExtractionId.fromRelativeUrl("Observation/xyz"), Optional.of(observation)));
                        assertThat(processedBatch.get("1").bundle().getValidResourceGroups()).containsExactly(
                                new ResourceGroup(ExtractionId.fromRelativeUrl("Observation/xyz"), "test"));
                    })
                    .verifyComplete();
        }
    }
}
