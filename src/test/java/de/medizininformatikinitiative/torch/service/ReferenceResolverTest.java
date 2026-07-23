package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.diagnostics.exclusions.BatchExclusions;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.ResourceExclusionReason;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.util.ReferenceExtractor;
import de.medizininformatikinitiative.torch.util.ReferenceHandler;
import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceResolverTest {

    @Mock
    CompartmentManager compartmentManager;
    @Mock
    ReferenceHandler referenceHandler;
    @Mock
    ReferenceExtractor referenceExtractor;
    @Mock
    ReferenceBundleLoader bundleLoader;

    ReferenceResolver resolver;

    static final String GROUP_ID = "obs-group";
    static final ExtractionId OBS_ID = new ExtractionId("Observation", "obs-1");
    static final ExtractionId COND_ID = new ExtractionId("Condition", "cond-1");

    @BeforeEach
    void setUp() {
        resolver = new ReferenceResolver(compartmentManager, referenceHandler, referenceExtractor, bundleLoader);
    }

    // -------------------------------------------------------------------------
    // loadReferencesByResourceGroup
    // -------------------------------------------------------------------------

    @Nested
    class LoadRefsByRG {

        BatchExclusions batchExclusions;

        @BeforeEach
        void setUpAcc() {
            batchExclusions = BatchExclusions.empty();
        }

        @Test
        void emptyResourceGroups_returnsEmptyMap() {
            var result = resolver.loadReferencesByResourceGroup(Set.of(), null, new ResourceBundle(), Map.of(), batchExclusions);
            assertThat(result).isEmpty();
        }

        @Test
        void patientResourceWithNullPatientBundle_marksInvalidAndRecordsDiagnostics() {
            var coreBundle = new ResourceBundle();
            var rg = new ResourceGroup(COND_ID, GROUP_ID);
            when(compartmentManager.isInCompartment(rg)).thenReturn(true);

            var result = resolver.loadReferencesByResourceGroup(Set.of(rg), null, coreBundle, Map.of(), batchExclusions);

            assertThat(result).isEmpty();
            assertThat(coreBundle.isValidResourceGroup(rg)).isFalse();
            assertThat(batchExclusions.getResourceExclusions())
                    .anySatisfy(event -> {
                        assertThat(event.reason()).isEqualTo(ResourceExclusionReason.RESOURCE_OUTSIDE_BATCH);
                        assertThat(event.groupId()).isEqualTo(GROUP_ID);
                        assertThat(event.resourceId()).isEqualTo(COND_ID.toString());
                    });
        }

        @Test
        void resourceNotInBundle_marksInvalidAndRecordsDiagnostics() {
            var coreBundle = new ResourceBundle();
            var rg = new ResourceGroup(OBS_ID, GROUP_ID);
            coreBundle.put(OBS_ID); // puts Optional.empty() → triggers handleMissingResource
            when(compartmentManager.isInCompartment(rg)).thenReturn(false);

            var result = resolver.loadReferencesByResourceGroup(Set.of(rg), null, coreBundle, Map.of(), batchExclusions);

            assertThat(result).isEmpty();
            assertThat(coreBundle.isValidResourceGroup(rg)).isFalse();
        }

        @Test
        void mustHaveViolated_marksInvalidAndRecordsDiagnostics() throws Exception {
            var obs = (Observation) new Observation().setId("obs-1");
            var coreBundle = new ResourceBundle();
            coreBundle.put(obs);
            var rg = new ResourceGroup(OBS_ID, GROUP_ID);
            AnnotatedAttributeGroup group = new AnnotatedAttributeGroup(GROUP_ID, "Observation",
                    "http://example.org/Profile", List.of(), List.of());
            when(compartmentManager.isInCompartment(rg)).thenReturn(false);
            when(referenceExtractor.extract(any(), anyMap(), anyString(), any(), any()))
                    .thenThrow(new MustHaveViolatedException.AttributeViolated("must-have violated", "someAttribute"));

            var result = resolver.loadReferencesByResourceGroup(
                    Set.of(rg), null, coreBundle, Map.of(GROUP_ID, group), batchExclusions);

            assertThat(result).isEmpty();
            assertThat(coreBundle.isValidResourceGroup(rg)).isFalse();
            assertThat(batchExclusions.getResourceExclusions())
                    .anySatisfy(event -> {
                        assertThat(event.reason()).isEqualTo(ResourceExclusionReason.MUST_HAVE);
                        assertThat(event.groupId()).isEqualTo(GROUP_ID);
                        assertThat(event.attributeRef()).isEqualTo("someAttribute");
                    });
        }

        @Test
        void mustHaveViolated_patientScope_marksInvalidAndRecordsPatientDiagnostics() throws Exception {
            var obs = (Observation) new Observation().setId("obs-1");
            var prb = new PatientResourceBundle("p1");
            prb.bundle().put(obs);
            var rg = new ResourceGroup(OBS_ID, GROUP_ID);
            AnnotatedAttributeGroup group = new AnnotatedAttributeGroup(GROUP_ID, "Observation",
                    "http://example.org/Profile", List.of(), List.of());
            when(compartmentManager.isInCompartment(rg)).thenReturn(true);
            when(referenceExtractor.extract(any(), anyMap(), anyString(), any(), any()))
                    .thenThrow(new MustHaveViolatedException.AttributeViolated("must-have violated", "someAttribute"));

            var result = resolver.loadReferencesByResourceGroup(
                    Set.of(rg), prb, new ResourceBundle(), Map.of(GROUP_ID, group), batchExclusions);

            assertThat(result).isEmpty();
            assertThat(prb.bundle().isValidResourceGroup(rg)).isFalse();
            assertThat(batchExclusions.getResourceExclusions())
                    .anySatisfy(event -> {
                        assertThat(event.reason()).isEqualTo(ResourceExclusionReason.MUST_HAVE);
                        assertThat(event.patientId()).isEqualTo("p1");
                        assertThat(event.attributeRef()).isEqualTo("someAttribute");
                    });
        }
    }

    // -------------------------------------------------------------------------
    // resolveCoreBundle
    // -------------------------------------------------------------------------

    @Nested
    class ResolveCoreBundle {

        @Test
        void emptyBundle_returnsBundle() {
            var coreBundle = new ResourceBundle();
            var result = resolver.resolveCoreBundle(coreBundle, Map.of(), BatchExclusions.empty()).block();
            assertThat(result).isSameAs(coreBundle);
        }

        @Test
        void withAcc_emptyBundle_returnsBundle() {
            var coreBundle = new ResourceBundle();
            var batchExclusions = BatchExclusions.empty();
            var result = resolver.resolveCoreBundle(coreBundle, Map.of(), batchExclusions).block();
            assertThat(result).isSameAs(coreBundle);
        }

        @Test
        void bundleWithValidGroup_resolvesAndReturns() throws Exception {
            var obs = (Observation) new Observation().setId("obs-1");
            var coreBundle = new ResourceBundle();
            coreBundle.put(obs);
            coreBundle.addResourceGroupValidity(new ResourceGroup(OBS_ID, GROUP_ID), true);
            when(compartmentManager.isInCompartment(any(ResourceGroup.class))).thenReturn(false);
            when(referenceExtractor.extract(any(), anyMap(), anyString(), any(), any())).thenReturn(List.of());

            var result = resolver.resolveCoreBundle(coreBundle, Map.of(), BatchExclusions.empty()).block();

            assertThat(result).isSameAs(coreBundle);
        }
    }

    // -------------------------------------------------------------------------
    // resolveUnknownCoreRefs
    // -------------------------------------------------------------------------

    @Nested
    class ResolveUnknownCoreRefs {

        @Test
        void emptyGroups_completesEmpty() {
            var coreBundle = new ResourceBundle();
            StepVerifier.create(resolver.resolveUnknownCoreRefs(Set.of(), coreBundle, Map.of(), BatchExclusions.empty()))
                    .verifyComplete();
        }

        @Test
        void emptyGroups_withAcc_completesEmpty() {
            var coreBundle = new ResourceBundle();
            var batchExclusions = BatchExclusions.empty();
            StepVerifier.create(resolver.resolveUnknownCoreRefs(Set.of(), coreBundle, Map.of(), batchExclusions))
                    .verifyComplete();
        }

        @Test
        void groupWithNoExtractedRefs_completesEmpty() throws Exception {
            var obs = (Observation) new Observation().setId("obs-1");
            var coreBundle = new ResourceBundle();
            coreBundle.put(obs);
            var rg = new ResourceGroup(OBS_ID, GROUP_ID);
            when(compartmentManager.isInCompartment(rg)).thenReturn(false);
            when(referenceExtractor.extract(any(), anyMap(), anyString(), any(), any())).thenReturn(List.of());

            StepVerifier.create(resolver.resolveUnknownCoreRefs(Set.of(rg), coreBundle, Map.of(), BatchExclusions.empty()))
                    .verifyComplete();
        }

        @Test
        void groupWithLinkedRef_fetchesAndRecordsNotFoundWhenMissing() throws Exception {
            var obs = (Observation) new Observation().setId("obs-1");
            var coreBundle = new ResourceBundle();
            coreBundle.put(obs);
            var rg = new ResourceGroup(OBS_ID, GROUP_ID);
            when(compartmentManager.isInCompartment(rg)).thenReturn(false);

            var attr = new AnnotatedAttribute("Obs.ref", "Obs.ref", false, List.of("linkedGrp"));
            var wrapper = new ReferenceWrapper(attr, List.of(COND_ID), GROUP_ID, OBS_ID);
            when(referenceExtractor.extract(any(), anyMap(), anyString(), any(), any())).thenReturn(List.of(wrapper));
            when(bundleLoader.fetchUnknownResources(eq(List.of(COND_ID)), eq("linkedGrp"), anyMap()))
                    .thenReturn(Mono.just(List.of()));
            when(referenceHandler.handleReferences(any(), isNull(), eq(coreBundle), anyMap(), anySet(), any()))
                    .thenReturn(Flux.empty());

            var batchExclusions = BatchExclusions.empty();
            StepVerifier.create(resolver.resolveUnknownCoreRefs(Set.of(rg), coreBundle, Map.of(), batchExclusions))
                    .verifyComplete();

            assertThat(batchExclusions.getResourceExclusions())
                    .anySatisfy(event -> {
                        assertThat(event.reason()).isEqualTo(ResourceExclusionReason.REFERENCE_NOT_FOUND);
                        assertThat(event.groupId()).isEqualTo("linkedGrp");
                        assertThat(event.resourceId()).isEqualTo(COND_ID.toString());
                    });
        }
    }

    // -------------------------------------------------------------------------
    // resolveUnknownPatientBatchRefs
    // -------------------------------------------------------------------------

    @Nested
    class ResolveUnknownPatientBatchRefs {

        @Test
        void groupWithLinkedRef_fetchesAndRecordsNotFoundWhenMissing() throws Exception {
            var obs = (Observation) new Observation().setId("obs-1");
            var prb = new PatientResourceBundle("p1");
            prb.bundle().put(obs);
            var rg = new ResourceGroup(OBS_ID, GROUP_ID);
            when(compartmentManager.isInCompartment(rg)).thenReturn(true);

            var attr = new AnnotatedAttribute("Obs.ref", "Obs.ref", false, List.of("linkedGrp"));
            var wrapper = new ReferenceWrapper(attr, List.of(COND_ID), GROUP_ID, OBS_ID);
            when(referenceExtractor.extract(any(), anyMap(), anyString(), any(), any())).thenReturn(List.of(wrapper));
            when(bundleLoader.fetchUnknownResources(eq(List.of(COND_ID)), eq("linkedGrp"), anyMap()))
                    .thenReturn(Mono.just(List.of()));
            when(referenceHandler.handleReferences(any(), eq(prb), any(), anyMap(), anySet(), any()))
                    .thenReturn(Flux.empty());

            var bwc = PatientBatchWithConsent.fromList(List.of(prb));
            var rgsPerPat = Map.of("p1", Set.of(rg));

            StepVerifier.create(resolver.resolveUnknownPatientBatchRefs(rgsPerPat, bwc, Map.of()))
                    .verifyComplete();

            assertThat(bwc.batchExclusions().getResourceExclusions())
                    .anySatisfy(event -> {
                        assertThat(event.reason()).isEqualTo(ResourceExclusionReason.REFERENCE_NOT_FOUND);
                        assertThat(event.patientId()).isEqualTo("p1");
                        assertThat(event.groupId()).isEqualTo("linkedGrp");
                    });
        }
    }

    // -------------------------------------------------------------------------
    // resolvePatientBatch
    // -------------------------------------------------------------------------

    @Nested
    class ResolvePatientBatch {

        @Test
        void emptyBatch_returnsEquivalentBatch() {
            var bwc = PatientBatchWithConsent.fromList(List.of());

            var result = resolver.resolvePatientBatch(bwc, Map.of()).block();

            assertThat(result).isNotNull();
            assertThat(result.bundles()).isEmpty();
        }

        @Test
        void batchWithOnePatient_noRefs_returnsUpdatedBatch() {
            var prb = new PatientResourceBundle("p1");
            var bwc = PatientBatchWithConsent.fromList(List.of(prb));

            var result = resolver.resolvePatientBatch(bwc, Map.of()).block();

            assertThat(result).isNotNull();
            assertThat(result.bundles()).containsKey("p1");
        }
    }
}
