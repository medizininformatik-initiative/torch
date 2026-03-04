package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.diagnostics.BatchDiagnosticsAcc;
import de.medizininformatikinitiative.torch.diagnostics.CriterionKeys;
import de.medizininformatikinitiative.torch.diagnostics.MustHaveEvaluation;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.fhir.Query;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.util.ProfileMustHaveChecker;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
    ConsentValidator consentValidator;

    @Mock
    DseMappingTreeBase dseMappingTreeBase;

    @Mock
    ProfileMustHaveChecker profileMustHaveChecker;

    DirectResourceLoader directResourceLoader;

    @BeforeEach
    void setUp() {
        directResourceLoader = new DirectResourceLoader(
                dataStore,
                dseMappingTreeBase,
                profileMustHaveChecker,
                consentValidator
        );
    }

    @Nested
    class ProcessCoreAttributeGroupCoverage {

        @Test
        void shouldPutValidTrue_andComplete_whenAtLeastOneResourceIsValid() {
            var attr = new AnnotatedAttribute("Observation.subject", "Observation.subject", true);
            var groupRef = "http://example.org/StructureDefinition/test";
            var group = new AnnotatedAttributeGroup("core", "Observation", groupRef, List.of(attr), List.of());

            Observation obs = new Observation();
            obs.setId("Observation/xyz");

            when(dataStore.search(any(Query.class), eq(DomainResource.class)))
                    .thenReturn(Flux.just(obs));

            MustHaveEvaluation ok = mock(MustHaveEvaluation.class);
            when(ok.applicable()).thenReturn(true);
            when(ok.fulfilled()).thenReturn(true);

            when(profileMustHaveChecker.evaluateFirst(eq(obs), eq(group)))
                    .thenReturn(ok);

            ResourceBundle bundle = mock(ResourceBundle.class);
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 1);

            StepVerifier.create(directResourceLoader.processCoreAttributeGroup(group, bundle, acc))
                    .verifyComplete();

            verify(bundle).put(obs, "core", true);
        }

        @Test
        void shouldPutValidFalse_andThenError_whenMustHaveAndNoValidResource() {
            var attr = new AnnotatedAttribute("Observation.subject", "Observation.subject", true);
            var groupRef = "http://example.org/StructureDefinition/test";
            var group = new AnnotatedAttributeGroup("core", "Observation", groupRef, List.of(attr), List.of());

            Observation obs = new Observation();
            obs.setId("Observation/xyz");

            when(dataStore.search(any(Query.class), eq(DomainResource.class)))
                    .thenReturn(Flux.just(obs));

            MustHaveEvaluation violated = mock(MustHaveEvaluation.class);
            when(violated.applicable()).thenReturn(true);
            when(violated.fulfilled()).thenReturn(false);
            when(violated.firstViolated()).thenReturn(Optional.of(attr));

            when(profileMustHaveChecker.evaluateFirst(eq(obs), eq(group)))
                    .thenReturn(violated);

            ResourceBundle bundle = mock(ResourceBundle.class);
            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 1);

            StepVerifier.create(directResourceLoader.processCoreAttributeGroup(group, bundle, acc))
                    .expectError(MustHaveViolatedException.class)
                    .verify();

            verify(bundle).put(obs, "core", false);

            var diag = acc.snapshot();
            assertThat(diag.criteria()).containsKey(CriterionKeys.mustHaveAttribute(group, attr));
            assertThat(diag.criteria().get(CriterionKeys.mustHaveAttribute(group, attr)).resourcesExcluded())
                    .isEqualTo(1);
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

            when(dataStore.search(any(), any())).thenReturn(Flux.empty());

            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 1);

            var result = directResourceLoader.processPatientAttributeGroups(
                    List.of(attributeGroup),
                    batchWithConsent,
                    safeSet,
                    acc
            );

            StepVerifier.create(result)
                    .assertNext(res -> {
                        assertThat(res.bundles()).containsKey("1");
                        assertThat(res.get("1").bundle().cache())
                                .doesNotContainKey(ExtractionId.fromRelativeUrl("Observation/xyz"));
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

            when(dataStore.search(any(), any())).thenReturn(Flux.just(new Observation()));

            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 1);

            var result = directResourceLoader.processPatientAttributeGroups(
                    List.of(attributeGroup),
                    batchWithConsent,
                    safeSet,
                    acc
            );

            StepVerifier.create(result)
                    .assertNext(res -> {
                        assertThat(res.bundles()).containsKey("1");
                        assertThat(res.get("1").bundle().cache())
                                .doesNotContainKey(ExtractionId.fromRelativeUrl("Observation/xyz"));
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

            when(dataStore.search(any(), any())).thenReturn(Flux.just(observation));

            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 1);

            var result = directResourceLoader.processPatientAttributeGroups(
                    List.of(attributeGroup),
                    batchWithConsent,
                    safeSet,
                    acc
            );

            StepVerifier.create(result)
                    .assertNext(res -> {
                        assertThat(res.bundles()).containsKey("1");
                        assertThat(res.get("1").bundle().cache())
                                .doesNotContainKey(ExtractionId.fromRelativeUrl("Observation/xyz"));
                    })
                    .verifyComplete();
        }

        @Test
        void testIgnoresResourceWithoutPatientReference() {
            var attribute = new AnnotatedAttribute("Observation.name", "Observation.name", false);
            var attributeGroup = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(attribute), List.of());
            var patientBundle = new PatientResourceBundle("1");
            var batchWithConsent = PatientBatchWithConsent.fromList(List.of(patientBundle));
            var safeSet = new HashSet<>(List.of("1"));

            Observation observation = new Observation();
            observation.setId("Observation/xyz");

            when(dataStore.search(any(), any())).thenReturn(Flux.just(observation));

            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 1);

            var result = directResourceLoader.processPatientAttributeGroups(
                    List.of(attributeGroup),
                    batchWithConsent,
                    safeSet,
                    acc
            );

            StepVerifier.create(result)
                    .assertNext(res -> {
                        assertThat(res.bundles()).containsKey("1");
                        assertThat(res.get("1").bundle().cache())
                                .doesNotContainKey(ExtractionId.fromRelativeUrl("Observation/xyz"));
                    })
                    .verifyComplete();
        }

        @Test
        void testStoresObservationWithInvalidMustHave() {
            var mustHaveAttr = new AnnotatedAttribute("Observation.id", "Observation.id", true);
            var attributeGroup = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(mustHaveAttr), List.of());

            var patientBundle = new PatientResourceBundle("1");
            var batchWithConsent = PatientBatchWithConsent.fromList(List.of(patientBundle));
            var safeSet = new HashSet<>(List.of("1"));

            Observation observation = new Observation();
            observation.setId("Observation/xyz");
            observation.setSubject(new Reference("Patient/1"));

            when(dataStore.search(any(), any())).thenReturn(Flux.just(observation));

            MustHaveEvaluation eval = mock(MustHaveEvaluation.class);
            when(eval.applicable()).thenReturn(true);
            when(eval.fulfilled()).thenReturn(false);
            when(eval.firstViolated()).thenReturn(Optional.of(mustHaveAttr));

            when(profileMustHaveChecker.evaluateFirst(observation, attributeGroup)).thenReturn(eval);

            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 1);

            var result = directResourceLoader.processPatientAttributeGroups(
                    List.of(attributeGroup),
                    batchWithConsent,
                    safeSet,
                    acc
            );

            StepVerifier.create(result)
                    .assertNext(processedBatch -> {
                        assertThat(processedBatch).isNotNull();
                        assertThat(processedBatch.patientBatch().ids()).containsExactly("1");

                        assertThat(processedBatch.get("1").bundle().cache())
                                .isEqualTo(Map.of(
                                        ExtractionId.fromRelativeUrl("Observation/xyz"),
                                        Optional.of(observation)
                                ));

                        assertThat(processedBatch.get("1").bundle().getValidResourceGroups())
                                .doesNotContain(new ResourceGroup(
                                        ExtractionId.fromRelativeUrl("Observation/xyz"),
                                        "test"
                                ));
                    })
                    .verifyComplete();

            var diag = acc.snapshot();
            assertThat(diag.criteria()).containsKey(CriterionKeys.mustHaveAttribute(attributeGroup, mustHaveAttr));
            assertThat(diag.criteria().get(CriterionKeys.mustHaveAttribute(attributeGroup, mustHaveAttr)).resourcesExcluded())
                    .isEqualTo(1);
        }

        @Test
        void testStoresObservationWithKnownPatient() {
            var mustHaveAttr = new AnnotatedAttribute("Observation.id", "Observation.id", true);
            var attributeGroup = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(mustHaveAttr), List.of());

            var patientBundle = new PatientResourceBundle("1");
            var batchWithConsent = PatientBatchWithConsent.fromList(List.of(patientBundle));
            var safeSet = new HashSet<>(List.of("1"));

            Observation observation = new Observation();
            observation.setId("Observation/xyz");
            observation.setSubject(new Reference("Patient/1"));

            when(dataStore.search(any(), any())).thenReturn(Flux.just(observation));

            MustHaveEvaluation eval = mock(MustHaveEvaluation.class);
            when(eval.applicable()).thenReturn(true);
            when(eval.fulfilled()).thenReturn(true);

            when(profileMustHaveChecker.evaluateFirst(observation, attributeGroup)).thenReturn(eval);

            BatchDiagnosticsAcc acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 1);

            var result = directResourceLoader.processPatientAttributeGroups(
                    List.of(attributeGroup),
                    batchWithConsent,
                    safeSet,
                    acc
            );

            StepVerifier.create(result)
                    .assertNext(processedBatch -> {
                        assertThat(processedBatch).isNotNull();
                        assertThat(processedBatch.patientBatch().ids()).containsExactly("1");

                        assertThat(processedBatch.get("1").bundle().cache())
                                .isEqualTo(Map.of(
                                        ExtractionId.fromRelativeUrl("Observation/xyz"),
                                        Optional.of(observation)
                                ));

                        assertThat(processedBatch.get("1").bundle().getValidResourceGroups())
                                .containsExactly(new ResourceGroup(
                                        ExtractionId.fromRelativeUrl("Observation/xyz"),
                                        "test"
                                ));
                    })
                    .verifyComplete();
        }
    }
}
