package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.diagnostics.ExclusionAcc;
import de.medizininformatikinitiative.torch.diagnostics.ExclusionKind;
import de.medizininformatikinitiative.torch.diagnostics.ExclusionRecord;
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
import org.hl7.fhir.r4.model.Condition;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
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

        ExclusionAcc writer;

        @BeforeEach
        void setUpWriter() {
            writer = new ExclusionAcc();
        }

        @Test
        void emptyResourceGroups_returnsEmptyMap() {
            var result = resolver.loadReferencesByResourceGroup(Set.of(), null, new ResourceBundle(), Map.of(), writer);
            assertThat(result).isEmpty();
        }

        @Test
        void patientResourceWithNullPatientBundle_marksInvalidAndRecordsDiagnostics() {
            var coreBundle = new ResourceBundle();
            var rg = new ResourceGroup(COND_ID, GROUP_ID);
            when(compartmentManager.isInCompartment(rg)).thenReturn(true);

            var result = resolver.loadReferencesByResourceGroup(Set.of(rg), null, coreBundle, Map.of(), writer);

            assertThat(result).isEmpty();
            assertThat(coreBundle.isValidResourceGroup(rg)).isFalse();
            List<ExclusionRecord> exclusions = writer.snapshot();
            assertThat(exclusions).hasSize(1);
            assertThat(exclusions.get(0).reason()).isEqualTo(ExclusionKind.REFERENCE_OUTSIDE_BATCH);
        }

        @Test
        void resourceNotInBundle_marksInvalidAndRecordsDiagnostics() {
            var coreBundle = new ResourceBundle();
            var rg = new ResourceGroup(OBS_ID, GROUP_ID);
            coreBundle.put(OBS_ID); // puts Optional.empty() → triggers handleMissingResource
            when(compartmentManager.isInCompartment(rg)).thenReturn(false);

            var result = resolver.loadReferencesByResourceGroup(Set.of(rg), null, coreBundle, Map.of(), writer);

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
            when(referenceExtractor.extract(any(), anyMap(), anyString()))
                    .thenThrow(new MustHaveViolatedException("must-have violated"));

            var result = resolver.loadReferencesByResourceGroup(
                    Set.of(rg), null, coreBundle, Map.of(GROUP_ID, group), writer);

            assertThat(result).isEmpty();
            assertThat(coreBundle.isValidResourceGroup(rg)).isFalse();
            List<ExclusionRecord> exclusions = writer.snapshot();
            assertThat(exclusions).hasSize(1);
            assertThat(exclusions.get(0).reason()).isEqualTo(ExclusionKind.MUST_HAVE_FIELD);
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
            var result = resolver.resolveCoreBundle(coreBundle, Map.of(), ExclusionAcc.noop()).block();
            assertThat(result).isSameAs(coreBundle);
        }

        @Test
        void withAcc_emptyBundle_returnsBundle() {
            var coreBundle = new ResourceBundle();
            var result = resolver.resolveCoreBundle(coreBundle, Map.of(), new ExclusionAcc()).block();
            assertThat(result).isSameAs(coreBundle);
        }

        @Test
        void bundleWithValidGroup_resolvesAndReturns() throws Exception {
            var obs = (Observation) new Observation().setId("obs-1");
            var coreBundle = new ResourceBundle();
            coreBundle.put(obs);
            coreBundle.addResourceGroupValidity(new ResourceGroup(OBS_ID, GROUP_ID), true);
            when(compartmentManager.isInCompartment(any(ResourceGroup.class))).thenReturn(false);
            when(referenceExtractor.extract(any(), anyMap(), anyString())).thenReturn(List.of());

            var result = resolver.resolveCoreBundle(coreBundle, Map.of(), ExclusionAcc.noop()).block();

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
            StepVerifier.create(resolver.resolveUnknownCoreRefs(Set.of(), coreBundle, Map.of(), ExclusionAcc.noop()))
                    .verifyComplete();
        }

        @Test
        void emptyGroups_withAcc_completesEmpty() {
            var coreBundle = new ResourceBundle();
            StepVerifier.create(resolver.resolveUnknownCoreRefs(Set.of(), coreBundle, Map.of(), new ExclusionAcc()))
                    .verifyComplete();
        }

        @Test
        void groupWithNoExtractedRefs_completesEmpty() throws Exception {
            var obs = (Observation) new Observation().setId("obs-1");
            var coreBundle = new ResourceBundle();
            coreBundle.put(obs);
            var rg = new ResourceGroup(OBS_ID, GROUP_ID);
            when(compartmentManager.isInCompartment(rg)).thenReturn(false);
            when(referenceExtractor.extract(any(), anyMap(), anyString())).thenReturn(List.of());

            StepVerifier.create(resolver.resolveUnknownCoreRefs(Set.of(rg), coreBundle, Map.of(), ExclusionAcc.noop()))
                    .verifyComplete();
        }

        @Test
        void unresolvedRef_recordsExclusionAndMarksGroupInvalid() throws Exception {
            var obs = (Observation) new Observation().setId("obs-1");
            var coreBundle = new ResourceBundle();
            coreBundle.put(obs);
            var rg = new ResourceGroup(OBS_ID, GROUP_ID);
            var refAttr = new AnnotatedAttribute("Observation.focus", "focus", false, List.of("cond-group"));
            var wrapper = new ReferenceWrapper(refAttr, List.of(COND_ID), GROUP_ID, OBS_ID);

            when(compartmentManager.isInCompartment(rg)).thenReturn(false);
            when(referenceExtractor.extract(any(), anyMap(), anyString())).thenReturn(List.of(wrapper));
            when(bundleLoader.fetchUnknownResources(anyList(), anyString(), anyMap()))
                    .thenReturn(Mono.just(List.of(new Observation().setId("obs-99"))));
            when(referenceHandler.handleReferences(anyList(), isNull(), any(), anyMap(), anySet()))
                    .thenReturn(Flux.empty());

            var condGroup = new AnnotatedAttributeGroup("cond-group", "Condition",
                    "http://example.org/cond-profile", List.of(), List.of());
            var writer = new ExclusionAcc();
            StepVerifier.create(resolver.resolveUnknownCoreRefs(
                            Set.of(rg), coreBundle, Map.of("cond-group", condGroup), writer))
                    .verifyComplete();

            List<ExclusionRecord> exclusions = writer.snapshot();
            assertThat(exclusions).hasSize(1);
            assertThat(exclusions.get(0).reason()).isEqualTo(ExclusionKind.REFERENCE_NOT_FOUND);
            assertThat(exclusions.get(0).resourceId()).isEqualTo("Condition/cond-1");
            assertThat(exclusions.get(0).groupRef()).isEqualTo("http://example.org/cond-profile");
            assertThat(coreBundle.isValidResourceGroup(new ResourceGroup(COND_ID, "cond-group"))).isFalse();
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

            var result = resolver.resolvePatientBatch(bwc, Map.of(), new ExclusionAcc()).block();

            assertThat(result).isNotNull();
            assertThat(result.bundles()).isEmpty();
        }

        @Test
        void batchWithOnePatient_noRefs_returnsUpdatedBatch() {
            var prb = new PatientResourceBundle("p1");
            var bwc = PatientBatchWithConsent.fromList(List.of(prb));

            var result = resolver.resolvePatientBatch(bwc, Map.of(), new ExclusionAcc()).block();

            assertThat(result).isNotNull();
            assertThat(result.bundles()).containsKey("p1");
        }

        @Test
        void unresolvedRef_recordsExclusionWithPatientId() throws Exception {
            var obs = (Observation) new Observation().setId("obs-1");
            var prb = new PatientResourceBundle("p1");
            prb.put(obs);
            var bwc = PatientBatchWithConsent.fromList(List.of(prb));

            var rg = new ResourceGroup(OBS_ID, GROUP_ID);
            var refAttr = new AnnotatedAttribute("Observation.focus", "focus", false, List.of("cond-group"));
            var wrapper = new ReferenceWrapper(refAttr, List.of(COND_ID), GROUP_ID, OBS_ID);

            when(compartmentManager.isInCompartment(rg)).thenReturn(true);
            when(referenceExtractor.extract(any(), anyMap(), anyString())).thenReturn(List.of(wrapper));
            when(bundleLoader.fetchUnknownResources(anyList(), anyString(), anyMap()))
                    .thenReturn(Mono.just(List.of(new Observation().setId("obs-99"))));
            when(referenceHandler.handleReferences(anyList(), any(PatientResourceBundle.class), any(), anyMap(), anySet()))
                    .thenReturn(Flux.empty());

            var condGroup = new AnnotatedAttributeGroup("cond-group", "Condition",
                    "http://example.org/cond-profile", List.of(), List.of());
            var writer = new ExclusionAcc();
            var RGsPerPat = Map.of("p1", Set.of(rg));
            StepVerifier.create(resolver.resolveUnknownPatientBatchRefs(
                            RGsPerPat, bwc, Map.of("cond-group", condGroup), writer))
                    .verifyComplete();

            List<ExclusionRecord> exclusions = writer.snapshot();
            assertThat(exclusions).hasSize(1);
            assertThat(exclusions.get(0).patientId()).isEqualTo("p1");
            assertThat(exclusions.get(0).reason()).isEqualTo(ExclusionKind.REFERENCE_NOT_FOUND);
            assertThat(exclusions.get(0).resourceId()).isEqualTo("Condition/cond-1");
            assertThat(exclusions.get(0).groupRef()).isEqualTo("http://example.org/cond-profile");
        }

        @Test
        void fetchedResource_matchingRef_cachesInPatientBundle() throws Exception {
            var obs = (Observation) new Observation().setId("obs-1");
            var prb = new PatientResourceBundle("p1");
            prb.put(obs);
            var bwc = PatientBatchWithConsent.fromList(List.of(prb));
            var rg = new ResourceGroup(OBS_ID, GROUP_ID);
            var refAttr = new AnnotatedAttribute("Observation.focus", "focus", false, List.of("cond-group"));
            var wrapper = new ReferenceWrapper(refAttr, List.of(COND_ID), GROUP_ID, OBS_ID);
            when(compartmentManager.isInCompartment(rg)).thenReturn(true);
            when(referenceExtractor.extract(any(), anyMap(), anyString())).thenReturn(List.of(wrapper));
            when(bundleLoader.fetchUnknownResources(anyList(), anyString(), anyMap()))
                    .thenReturn(Mono.just(List.of(new Condition().setId("cond-1"))));
            when(referenceHandler.handleReferences(anyList(), any(PatientResourceBundle.class), any(), anyMap(), anySet()))
                    .thenReturn(Flux.empty());
            var writer = new ExclusionAcc();
            var RGsPerPat = Map.of("p1", Set.of(rg));
            StepVerifier.create(resolver.resolveUnknownPatientBatchRefs(RGsPerPat, bwc, Map.of(), writer))
                    .verifyComplete();
            assertThat(writer.snapshot()).isEmpty();
        }

        @Test
        void resolvedRef_returnsNewResourceGroups() throws Exception {
            var obs = (Observation) new Observation().setId("obs-1");
            var prb = new PatientResourceBundle("p1");
            prb.put(obs);
            var bwc = PatientBatchWithConsent.fromList(List.of(prb));
            var rg = new ResourceGroup(OBS_ID, GROUP_ID);
            var refAttr = new AnnotatedAttribute("Observation.focus", "focus", false, List.of("cond-group"));
            var wrapper = new ReferenceWrapper(refAttr, List.of(COND_ID), GROUP_ID, OBS_ID);
            when(compartmentManager.isInCompartment(rg)).thenReturn(true);
            when(referenceExtractor.extract(any(), anyMap(), anyString())).thenReturn(List.of(wrapper));
            when(bundleLoader.fetchUnknownResources(anyList(), anyString(), anyMap()))
                    .thenReturn(Mono.just(List.of()));
            when(referenceHandler.handleReferences(anyList(), any(PatientResourceBundle.class), any(), anyMap(), anySet()))
                    .thenReturn(Flux.just(new ResourceGroup(COND_ID, "cond-group")));
            var RGsPerPat = Map.of("p1", Set.of(rg));
            StepVerifier.create(resolver.resolveUnknownPatientBatchRefs(RGsPerPat, bwc, Map.of(), new ExclusionAcc()))
                    .assertNext(result -> assertThat(result).containsKey("p1"))
                    .verifyComplete();
        }
    }
}
