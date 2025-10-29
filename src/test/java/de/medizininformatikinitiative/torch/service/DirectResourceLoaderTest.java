package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.management.StructureDefinitionHandler;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.util.ProfileMustHaveChecker;
import org.hl7.fhir.r4.model.Observation;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    class ProcessPatientAttributeGroups {

        @Test
        void testIgnoresEmptyFlux() {

            var attribute = new AnnotatedAttribute("Observation.name", "Observation.name", false);
            var attributeGroup = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(attribute), List.of(), null);
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
                        assertThat(res.get("1").bundle().cache()).doesNotContainKey("Observation/xyz");
                    })
                    .verifyComplete();
        }

        @Test
        void testIgnoresEmptyResource() {

            var attribute = new AnnotatedAttribute("Observation.name", "Observation.name", false);
            var attributeGroup = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(attribute), List.of(), null);
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
                        assertThat(res.get("1").bundle().cache()).doesNotContainKey("Observation/xyz");
                    })
                    .verifyComplete();
        }


        @Test
        void testIgnoresObservationOfUnknownPatient() {

            var attribute = new AnnotatedAttribute("Observation.name", "Observation.name", false);
            var attributeGroup = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(attribute), List.of(), null);

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
                        assertThat(res.get("1").bundle().cache()).doesNotContainKey("Observation/xyz");
                    })
                    .verifyComplete();
        }

        @Test
        void testIgnoresResourceWithoutPatientReference() {
            // Arrange
            var attribute = new AnnotatedAttribute("Observation.name", "Observation.name", false);
            var attributeGroup = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(attribute), List.of(), null);
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
                        assertThat(res.get("1").bundle().cache()).doesNotContainKey("Observation/xyz");
                    })
                    .verifyComplete();
        }


        @Test
        void testStoresObservationWithInvalidMustHave() {
            // Arrange
            var attribute = new AnnotatedAttribute("Observation.name", "Observation.name", false);
            var attributeGroup = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(attribute), List.of(), null);
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
                                .isEqualTo(Map.of("Observation/xyz", Optional.of(observation)));
                        assertThat(processedBatch.get("1").bundle().getValidResourceGroups()).doesNotContain(
                                new ResourceGroup("Observation/xyz", "test"));
                    })
                    .verifyComplete();
        }


        @Test
        void testStoresObservationWithKnownPatient() {
            // Arrange
            var attribute = new AnnotatedAttribute("Observation.name", "Observation.name", false);
            var attributeGroup = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(attribute), List.of(), null);
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
                                .isEqualTo(Map.of("Observation/xyz", Optional.of(observation)));
                        assertThat(processedBatch.get("1").bundle().getValidResourceGroups()).containsExactly(
                                new ResourceGroup("Observation/xyz", "test"));
                    })
                    .verifyComplete();
        }
    }
}
