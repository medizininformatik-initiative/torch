package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.diagnostics.exclusions.BatchExclusions;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.ResourceExclusionEvent;
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
import org.hl7.fhir.r4.model.Encounter;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
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


        @Test
        void emptyResourceGroups_returnsEmptyMap() {
            var result = resolver.loadReferencesByResourceGroup(Set.of(), null, new ResourceBundle(), Map.of(), BatchExclusions.empty());
            assertThat(result).isEmpty();
        }

        @Test
        void patientResourceWithNullPatientBundle_marksInvalidAndRecordsDiagnostics() {
            var coreBundle = new ResourceBundle();
            var rg = new ResourceGroup(COND_ID, GROUP_ID);
            var exclusions = BatchExclusions.empty();
            when(compartmentManager.isInCompartment(rg)).thenReturn(true);

            var result = resolver.loadReferencesByResourceGroup(Set.of(rg), null, coreBundle, Map.of(), exclusions);

            assertThat(result).isEmpty();
            assertThat(coreBundle.isValidResourceGroup(rg)).isFalse();
            assertThat(exclusions.getResourceExclusions()).containsExactly(new ResourceExclusionEvent(ResourceExclusionReason.RESOURCE_OUTSIDE_BATCH, GROUP_ID, COND_ID.toRelativeUrl(), "",
                    ""));
        }

        @Test
        void resourceNotInBundle_marksInvalidAndRecordsDiagnostics() {
            var coreBundle = new ResourceBundle();
            var rg = new ResourceGroup(OBS_ID, GROUP_ID);
            coreBundle.put(OBS_ID); // puts Optional.empty() → triggers handleMissingResource
            when(compartmentManager.isInCompartment(rg)).thenReturn(false);

            var result = resolver.loadReferencesByResourceGroup(Set.of(rg), null, coreBundle, Map.of(), BatchExclusions.empty());

            assertThat(result).isEmpty();
            assertThat(coreBundle.isValidResourceGroup(rg)).isFalse();
        }

        @Test
        void mustHaveViolated_marksInvalidAndRecordsDiagnostics() throws Exception {
            var obs = (Observation) new Observation().setId("obs-1");
            var coreBundle = new ResourceBundle();
            coreBundle.put(obs);
            var exclusions = BatchExclusions.empty();
            var rg = new ResourceGroup(OBS_ID, GROUP_ID);
            AnnotatedAttributeGroup group = new AnnotatedAttributeGroup(GROUP_ID, "Observation",
                    "http://example.org/Profile", List.of(), List.of());
            when(compartmentManager.isInCompartment(rg)).thenReturn(false);
            when(referenceExtractor.extract(any(), anyMap(), anyString()))
                    .thenThrow(new MustHaveViolatedException.AttributeViolated("must-have violated", "attribute.ref.64715"));

            var result = resolver.loadReferencesByResourceGroup(
                    Set.of(rg), null, coreBundle, Map.of(GROUP_ID, group), exclusions);

            assertThat(result).isEmpty();
            assertThat(coreBundle.isValidResourceGroup(rg)).isFalse();
            assertThat(exclusions.getResourceExclusions()).containsExactly(new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE, GROUP_ID, OBS_ID.toRelativeUrl(), "",
                    "attribute.ref.64715"));
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
            var result = resolver.resolveCoreBundle(coreBundle, Map.of(), BatchExclusions.empty()).block();
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
            StepVerifier.create(resolver.resolveUnknownCoreRefs(Set.of(), coreBundle, Map.of(), BatchExclusions.empty()))
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

            StepVerifier.create(resolver.resolveUnknownCoreRefs(Set.of(rg), coreBundle, Map.of(), BatchExclusions.empty()))
                    .verifyComplete();
        }

        @Test
        void polymorphicReference_onlyFetchesMatchingResourceTypePerLinkedGroup() throws Exception {
            var obs = (Observation) new Observation().setId("obs-1");
            var coreBundle = new ResourceBundle();
            coreBundle.put(obs);
            var rg = new ResourceGroup(OBS_ID, GROUP_ID);
            when(compartmentManager.isInCompartment(rg)).thenReturn(false);

            // Encounter.diagnosis.condition is Reference(Condition|Procedure): one attribute, two linked groups
            // of different resource types, but the extracted reference is only ever a Condition.
            var attr = new AnnotatedAttribute("Encounter.diagnosis.condition", "Encounter.diagnosis.condition", false,
                    List.of("procGroup", "condGroup"));
            var wrapper = new ReferenceWrapper(attr, List.of(COND_ID), GROUP_ID, OBS_ID);
            when(referenceExtractor.extract(any(), anyMap(), anyString())).thenReturn(List.of(wrapper));

            var procGroup = new AnnotatedAttributeGroup("procGroup", "Procedure", "http://example.org/Procedure", List.of(), List.of());
            var condGroup = new AnnotatedAttributeGroup("condGroup", "Condition", "http://example.org/Condition", List.of(), List.of());
            var groupMap = Map.of("procGroup", procGroup, "condGroup", condGroup);
            var exclusions = BatchExclusions.empty();

            // Server can't find the Condition either, so we can observe how each linked group reacts
            // to a missing reference instead of only checking what was requested.
            when(bundleLoader.fetchUnknownResources(anyList(), anyString(), anyMap())).thenReturn(Mono.just(List.of()));
            when(referenceHandler.handleReferences(anyList(), isNull(), any(), anyMap(), anySet())).thenReturn(Flux.empty());

            StepVerifier.create(resolver.resolveUnknownCoreRefs(Set.of(rg), coreBundle, groupMap, exclusions))
                    .verifyComplete();

            // No FHIRSearch is wasted querying procGroup for a reference it can never match.
            verify(bundleLoader).fetchUnknownResources(eq(List.of(COND_ID)), eq("condGroup"), anyMap());
            verify(bundleLoader).fetchUnknownResources(eq(List.of()), eq("procGroup"), anyMap());

            // The missing reference is attributed to the linked group whose type it actually matches...
            assertThat(coreBundle.isValidResourceGroup(new ResourceGroup(COND_ID, "condGroup"))).isFalse();
            // ...and never even considered under the linked group of the wrong type.
            assertThat(coreBundle.contains(new ResourceGroup(COND_ID, "procGroup"))).isFalse();

            // Counted once (under condGroup), not twice as it was before the fix, when procGroup was
            // also queried for a Condition reference it could never resolve.
            assertThat(exclusions.getResourceExclusions()).containsExactly(new ResourceExclusionEvent(
                    ResourceExclusionReason.REFERENCE_NOT_FOUND, "condGroup", COND_ID.toRelativeUrl(), "", ""));
        }
    }

    // -------------------------------------------------------------------------
    // resolveUnknownPatientBatchRefs
    // -------------------------------------------------------------------------

    @Nested
    class ResolveUnknownPatientBatchRefs {

        @Test
        void polymorphicReference_onlyFetchesMatchingResourceTypePerLinkedGroup() throws Exception {
            var encId = new ExtractionId("Encounter", "enc-1");
            var enc = new Encounter().setId("enc-1");
            var prb = new PatientResourceBundle("p1");
            prb.bundle().put(enc);
            var batch = PatientBatchWithConsent.fromList(List.of(prb));
            var rg = new ResourceGroup(encId, GROUP_ID);
            when(compartmentManager.isInCompartment(rg)).thenReturn(true);

            // Encounter.diagnosis.condition is Reference(Condition|Procedure): one attribute, two linked groups
            // of different resource types, but the extracted reference is only ever a Condition.
            var attr = new AnnotatedAttribute("Encounter.diagnosis.condition", "Encounter.diagnosis.condition", false,
                    List.of("procGroup", "condGroup"));
            var wrapper = new ReferenceWrapper(attr, List.of(COND_ID), GROUP_ID, encId);
            when(referenceExtractor.extract(any(), anyMap(), anyString())).thenReturn(List.of(wrapper));

            var procGroup = new AnnotatedAttributeGroup("procGroup", "Procedure", "http://example.org/Procedure", List.of(), List.of());
            var condGroup = new AnnotatedAttributeGroup("condGroup", "Condition", "http://example.org/Condition", List.of(), List.of());
            var groupMap = Map.of("procGroup", procGroup, "condGroup", condGroup);

            // Server can't find the Condition either, so we can observe how each linked group reacts
            // to a missing reference instead of only checking what was requested.
            when(bundleLoader.fetchUnknownResources(anyList(), anyString(), anyMap())).thenReturn(Mono.just(List.of()));
            when(referenceHandler.handleReferences(anyList(), any(), any(), anyMap(), anySet())).thenReturn(Flux.empty());


            StepVerifier.create(resolver.resolveUnknownPatientBatchRefs(Map.of("p1", Set.of(rg)), batch, groupMap))
                    .verifyComplete();

            // No FHIRSearch is wasted querying procGroup for a reference it can never match.
            verify(bundleLoader).fetchUnknownResources(eq(List.of(COND_ID)), eq("condGroup"), anyMap());
            verify(bundleLoader).fetchUnknownResources(eq(List.of()), eq("procGroup"), anyMap());

            // The missing reference is attributed to the linked group whose type it actually matches...
            assertThat(prb.bundle().isValidResourceGroup(new ResourceGroup(COND_ID, "condGroup"))).isFalse();
            // ...and never even considered under the linked group of the wrong type.
            assertThat(prb.bundle().contains(new ResourceGroup(COND_ID, "procGroup"))).isFalse();

            // Counted once (under condGroup), not twice as it was before the fix, when procGroup was
            // also queried for a Condition reference it could never resolve.
            assertThat(batch.batchExclusions().getResourceExclusions()).containsExactly(new ResourceExclusionEvent(
                    ResourceExclusionReason.REFERENCE_NOT_FOUND, "condGroup", COND_ID.toRelativeUrl(), "p1", ""));
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
