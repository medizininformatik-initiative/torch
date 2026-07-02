package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.diagnostics.ExclusionAcc;
import de.medizininformatikinitiative.torch.diagnostics.ExclusionKind;
import de.medizininformatikinitiative.torch.diagnostics.ExclusionRecord;
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
        void processCoreAttributeGroup_withoutMustHave_andNoResources_completes() {
            var attr = new AnnotatedAttribute("Observation.code", "Observation.code", false);
            var group = new AnnotatedAttributeGroup("core", "Observation", "groupRef", List.of(attr), List.of());

            when(dataStore.search(any(Query.class), eq(DomainResource.class)))
                    .thenReturn(Flux.empty());

            ResourceBundle bundle = mock(ResourceBundle.class);

            StepVerifier.create(directResourceLoader.processCoreAttributeGroup(group, bundle, new ExclusionAcc()))
                    .verifyComplete();
        }

        @Test
        void processCoreAttributeGroup_withMustHave_andNoResources_errors() {
            var attr = new AnnotatedAttribute("Observation.code", "Observation.code", true);
            var group = new AnnotatedAttributeGroup("core", "Observation", "groupRef", List.of(attr), List.of());

            when(dataStore.search(any(Query.class), eq(DomainResource.class)))
                    .thenReturn(Flux.empty());

            ResourceBundle bundle = mock(ResourceBundle.class);

            StepVerifier.create(directResourceLoader.processCoreAttributeGroup(group, bundle, new ExclusionAcc()))
                    .expectError(MustHaveViolatedException.class)
                    .verify();
        }

        @Test
        void processCoreAttributeGroup_nonApplicable_putsInvalidWithoutMustHaveDiagnostic() {
            var attr = new AnnotatedAttribute("Observation.code", "Observation.code", true);
            var group = new AnnotatedAttributeGroup("core", "Observation", "groupRef", List.of(attr), List.of());

            Observation obs = new Observation();
            obs.setId("Observation/xyz");

            when(dataStore.search(any(Query.class), eq(DomainResource.class)))
                    .thenReturn(Flux.just(obs));

            MustHaveEvaluation eval = new MustHaveEvaluation.NotApplicable();
            when(profileMustHaveChecker.evaluateFirst(eq(obs), eq(group))).thenReturn(eval);

            ResourceBundle bundle = mock(ResourceBundle.class);
            ExclusionAcc writer = new ExclusionAcc();

            StepVerifier.create(directResourceLoader.processCoreAttributeGroup(group, bundle, writer))
                    .expectError(MustHaveViolatedException.class)
                    .verify();

            verify(bundle).put(obs, "core", false);

            // NotApplicable resources should not produce a MUST_HAVE_FIELD exclusion record
            assertThat(writer.snapshot())
                    .noneMatch(r -> r.reason() == ExclusionKind.MUST_HAVE_FIELD
                            && "groupRef".equals(r.groupRef()));
        }

        @Test
        void shouldPutValidTrue_andComplete_whenAtLeastOneResourceIsValid() {
            var attr = new AnnotatedAttribute("Observation.subject", "Observation.subject", true);
            var groupRef = "http://example.org/StructureDefinition/test";
            var group = new AnnotatedAttributeGroup("core", "Observation", groupRef, List.of(attr), List.of());

            Observation obs = new Observation();
            obs.setId("Observation/xyz");

            when(dataStore.search(any(Query.class), eq(DomainResource.class)))
                    .thenReturn(Flux.just(obs));

            MustHaveEvaluation ok = new MustHaveEvaluation.Fulfilled();
            when(profileMustHaveChecker.evaluateFirst(eq(obs), eq(group))).thenReturn(ok);

            ResourceBundle bundle = mock(ResourceBundle.class);

            StepVerifier.create(directResourceLoader.processCoreAttributeGroup(group, bundle, new ExclusionAcc()))
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

            MustHaveEvaluation violated = new MustHaveEvaluation.Violated(attr);
            when(profileMustHaveChecker.evaluateFirst(eq(obs), eq(group))).thenReturn(violated);

            ResourceBundle bundle = mock(ResourceBundle.class);
            ExclusionAcc writer = new ExclusionAcc();

            StepVerifier.create(directResourceLoader.processCoreAttributeGroup(group, bundle, writer))
                    .expectError(MustHaveViolatedException.class)
                    .verify();

            verify(bundle).put(obs, "core", false);

            List<ExclusionRecord> exclusions = writer.snapshot();
            assertThat(exclusions).hasSize(1);
            assertThat(exclusions.get(0).reason()).isEqualTo(ExclusionKind.MUST_HAVE_FIELD);
            assertThat(exclusions.get(0).resourceId()).isEqualTo("Observation/xyz");
        }
    }

    @Nested
    class ProcessPatientAttributeGroups {
        @Test
        void processPatientAttributeGroups_consentDenied_dropsResource_andCountsDiagnostic() {
            var attr = new AnnotatedAttribute("Observation.code", "Observation.code", false);
            var group = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(attr), List.of());

            var patientBundle = new PatientResourceBundle("1");
            var batchWithConsent = new PatientBatchWithConsent(
                    Map.of("1", patientBundle),
                    true,
                    new ResourceBundle(),
                    UUID.randomUUID()
            );
            var safeSet = new HashSet<>(List.of("1"));

            Observation observation = new Observation();
            observation.setId("Observation/xyz");
            observation.setSubject(new Reference("Patient/1"));

            when(dataStore.search(any(), any())).thenReturn(Flux.just(observation));
            when(consentValidator.checkConsent(eq(observation), eq(batchWithConsent))).thenReturn(false);

            ExclusionAcc writer = new ExclusionAcc();

            StepVerifier.create(directResourceLoader.processPatientAttributeGroups(
                            List.of(group),
                            batchWithConsent,
                            safeSet,
                            writer
                    ))
                    .assertNext(res -> {
                        assertThat(res.get("1").bundle().cache())
                                .doesNotContainKey(ExtractionId.fromRelativeUrl("Observation/xyz"));
                    })
                    .verifyComplete();

            List<ExclusionRecord> exclusions = writer.snapshot();
            assertThat(exclusions).hasSize(1);
            assertThat(exclusions.get(0).reason()).isEqualTo(ExclusionKind.CONSENT);
            assertThat(exclusions.get(0).patientId()).isEqualTo("1");
            assertThat(exclusions.get(0).resourceId()).isEqualTo("Observation/xyz");
        }

        @Test
        void processPatientAttributeGroups_nonApplicable_marksInvalid_andRemovesPatientFromSafeSet() {
            var mustHaveAttr = new AnnotatedAttribute("Observation.code", "Observation.code", true);
            var group = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(mustHaveAttr), List.of());

            var patientBundle = new PatientResourceBundle("1");
            var batchWithConsent = PatientBatchWithConsent.fromList(List.of(patientBundle));
            var safeSet = new HashSet<>(List.of("1"));

            Observation observation = new Observation();
            observation.setId("Observation/xyz");
            observation.setSubject(new Reference("Patient/1"));

            when(dataStore.search(any(), any())).thenReturn(Flux.just(observation));

            MustHaveEvaluation eval = new MustHaveEvaluation.NotApplicable();
            when(profileMustHaveChecker.evaluateFirst(observation, group)).thenReturn(eval);

            ExclusionAcc writer = new ExclusionAcc();

            StepVerifier.create(directResourceLoader.processPatientAttributeGroups(
                            List.of(group),
                            batchWithConsent,
                            safeSet,
                            writer
                    ))
                    .assertNext(processedBatch -> {
                        assertThat(processedBatch.get("1").bundle().cache())
                                .containsEntry(
                                        ExtractionId.fromRelativeUrl("Observation/xyz"),
                                        Optional.of(observation)
                                );

                        assertThat(processedBatch.get("1").bundle().getValidResourceGroups())
                                .doesNotContain(new ResourceGroup(
                                        ExtractionId.fromRelativeUrl("Observation/xyz"),
                                        "test"
                                ));
                    })
                    .verifyComplete();

            assertThat(safeSet).isEmpty();

            // Patient had a resource but none fulfilled must-have → MUST_HAVE_RESOURCE patient-level record
            List<ExclusionRecord> exclusions = writer.snapshot();
            assertThat(exclusions).hasSize(1);
            assertThat(exclusions.get(0).patientId()).isEqualTo("1");
            assertThat(exclusions.get(0).reason()).isEqualTo(ExclusionKind.MUST_HAVE_RESOURCE);
            assertThat(exclusions.get(0).groupRef()).isEqualTo("groupRef");
        }

        @Test
        void directLoadPatientCompartment_keepsOnlySafePatients() {
            var mustHaveAttr = new AnnotatedAttribute("Observation.code", "Observation.code", true);
            var group = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(mustHaveAttr), List.of());

            var p1 = new PatientResourceBundle("1");
            var p2 = new PatientResourceBundle("2");

            var batch = new PatientBatchWithConsent(
                    Map.of("1", p1, "2", p2),
                    false,
                    new ResourceBundle(),
                    UUID.randomUUID()
            );

            Observation obs1 = new Observation();
            obs1.setId("Observation/o1");
            obs1.setSubject(new Reference("Patient/1"));

            when(dataStore.search(any(), any())).thenReturn(Flux.just(obs1));

            MustHaveEvaluation eval = new MustHaveEvaluation.Fulfilled();
            when(profileMustHaveChecker.evaluateFirst(obs1, group)).thenReturn(eval);

            StepVerifier.create(directResourceLoader.directLoadPatientCompartment(
                            List.of(group),
                            batch,
                            new ExclusionAcc()
                    ))
                    .assertNext(result -> {
                        assertThat(result.bundles()).containsKey("1");
                        assertThat(result.bundles()).doesNotContainKey("2");
                        assertThat(result.patientBatch().ids()).containsExactly("1");
                    })
                    .verifyComplete();
        }

        @Test
        void testIgnoresEmptyFlux() {
            var attribute = new AnnotatedAttribute("Observation.name", "Observation.name", false);
            var attributeGroup = new AnnotatedAttributeGroup("test", "Observation", "groupRef", List.of(attribute), List.of());
            var patientBundle = new PatientResourceBundle("1");
            var batchWithConsent = PatientBatchWithConsent.fromList(List.of(patientBundle));
            var safeSet = new HashSet<>(List.of("1"));

            when(dataStore.search(any(), any())).thenReturn(Flux.empty());

            var result = directResourceLoader.processPatientAttributeGroups(
                    List.of(attributeGroup),
                    batchWithConsent,
                    safeSet,
                    new ExclusionAcc()
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

            var result = directResourceLoader.processPatientAttributeGroups(
                    List.of(attributeGroup),
                    batchWithConsent,
                    safeSet,
                    new ExclusionAcc()
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

            var result = directResourceLoader.processPatientAttributeGroups(
                    List.of(attributeGroup),
                    batchWithConsent,
                    safeSet,
                    new ExclusionAcc()
            );

            StepVerifier.create(result)
                    .assertNext(res -> assertThat(res.bundles()).containsKey("1"))
                    .verifyComplete();
        }
    }
}
