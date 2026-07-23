package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.diagnostics.BatchDiagnosticsAcc;
import de.medizininformatikinitiative.torch.diagnostics.CriterionKeys;
import de.medizininformatikinitiative.torch.exceptions.MustHaveViolatedException;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.consent.PatientBatchWithConsent;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.extraction.IdentifierReference;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.util.ReferenceExtractor;
import de.medizininformatikinitiative.torch.util.ReferenceHandler;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

        BatchDiagnosticsAcc acc;

        @BeforeEach
        void setUpAcc() {
            acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 1);
        }

        @Test
        void emptyResourceGroups_returnsEmptyMap() {
            var result = resolver.loadReferencesByResourceGroup(Set.of(), null, new ResourceBundle(), Map.of(), acc);
            assertThat(result).isEmpty();
        }

        @Test
        void patientResourceWithNullPatientBundle_marksInvalidAndRecordsDiagnostics() {
            var coreBundle = new ResourceBundle();
            var rg = new ResourceGroup(COND_ID, GROUP_ID);
            when(compartmentManager.isInCompartment(rg)).thenReturn(true);

            var result = resolver.loadReferencesByResourceGroup(Set.of(rg), null, coreBundle, Map.of(), acc);

            assertThat(result).isEmpty();
            assertThat(coreBundle.isValidResourceGroup(rg)).isFalse();
            var snapshot = acc.snapshot(0);
            var expectedKey = CriterionKeys.referenceOutsideBatch(COND_ID.resourceType());
            assertThat(snapshot.countsFor(expectedKey)).isPresent();
            assertThat(snapshot.countsFor(expectedKey).orElseThrow().resourcesExcluded()).isEqualTo(1);
        }

        @Test
        void resourceNotInBundle_marksInvalidAndRecordsDiagnostics() {
            var coreBundle = new ResourceBundle();
            var rg = new ResourceGroup(OBS_ID, GROUP_ID);
            coreBundle.put(OBS_ID); // puts Optional.empty() → triggers handleMissingResource
            when(compartmentManager.isInCompartment(rg)).thenReturn(false);

            var result = resolver.loadReferencesByResourceGroup(Set.of(rg), null, coreBundle, Map.of(), acc);

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
                    Set.of(rg), null, coreBundle, Map.of(GROUP_ID, group), acc);

            assertThat(result).isEmpty();
            assertThat(coreBundle.isValidResourceGroup(rg)).isFalse();
            var snapshot = acc.snapshot(0);
            var expectedKey = CriterionKeys.mustHaveGroup(group);
            assertThat(snapshot.countsFor(expectedKey)).isPresent();
            assertThat(snapshot.countsFor(expectedKey).orElseThrow().resourcesExcluded()).isEqualTo(1);
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
            var result = resolver.resolveCoreBundle(coreBundle, Map.of(), BatchDiagnosticsAcc.noop()).block();
            assertThat(result).isSameAs(coreBundle);
        }

        @Test
        void withAcc_emptyBundle_returnsBundle() {
            var coreBundle = new ResourceBundle();
            var acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 0);
            var result = resolver.resolveCoreBundle(coreBundle, Map.of(), acc).block();
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

            var result = resolver.resolveCoreBundle(coreBundle, Map.of(), BatchDiagnosticsAcc.noop()).block();

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
            StepVerifier.create(resolver.resolveUnknownCoreRefs(Set.of(), coreBundle, Map.of(), BatchDiagnosticsAcc.noop()))
                    .verifyComplete();
        }

        @Test
        void emptyGroups_withAcc_completesEmpty() {
            var coreBundle = new ResourceBundle();
            var acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 0);
            StepVerifier.create(resolver.resolveUnknownCoreRefs(Set.of(), coreBundle, Map.of(), acc))
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

            StepVerifier.create(resolver.resolveUnknownCoreRefs(Set.of(rg), coreBundle, Map.of(), BatchDiagnosticsAcc.noop()))
                    .verifyComplete();
        }

        @Test
        void identifierOnlyReference_resolvesAndMergesIntoReplayedWrapper() throws Exception {
            var obs = (Observation) new Observation().setId("obs-1");
            var coreBundle = new ResourceBundle();
            coreBundle.put(obs);
            var rg = new ResourceGroup(OBS_ID, GROUP_ID);

            var identifierRef = new IdentifierReference("http://system", "val-1");
            var refAttribute = new AnnotatedAttribute("Observation.subject", "Observation.subject", false, List.of("linkedGroup"));
            var wrapper = new ReferenceWrapper(refAttribute, List.of(), List.of(identifierRef), GROUP_ID, OBS_ID);

            var patient = new Patient();
            patient.setId("Patient/42");
            patient.addIdentifier(new Identifier().setSystem("http://system").setValue("val-1"));

            when(compartmentManager.isInCompartment(rg)).thenReturn(false);
            when(referenceExtractor.extract(any(), anyMap(), anyString())).thenReturn(List.of(wrapper));
            when(bundleLoader.fetchUnknownResources(eq(List.of()), eq("linkedGroup"), anyMap()))
                    .thenReturn(Mono.just(List.of()));
            when(bundleLoader.fetchByIdentifier(eq(List.of(identifierRef)), eq("linkedGroup"), anyMap()))
                    .thenReturn(Mono.just(List.of(patient)));
            when(referenceHandler.handleReferences(any(), isNull(), eq(coreBundle), anyMap(), anySet()))
                    .thenReturn(Flux.empty());

            StepVerifier.create(resolver.resolveUnknownCoreRefs(Set.of(rg), coreBundle, Map.of(), BatchDiagnosticsAcc.noop()))
                    .verifyComplete();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<ReferenceWrapper>> captor = ArgumentCaptor.forClass(List.class);
            verify(referenceHandler).handleReferences(captor.capture(), isNull(), eq(coreBundle), anyMap(), anySet());
            assertThat(captor.getValue()).hasSize(1);
            assertThat(captor.getValue().getFirst().references()).containsExactly(ExtractionId.fromRelativeUrl("Patient/42"));
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
            var acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 0);

            var result = resolver.resolvePatientBatch(bwc, Map.of(), acc).block();

            assertThat(result).isNotNull();
            assertThat(result.bundles()).isEmpty();
        }

        @Test
        void batchWithOnePatient_noRefs_returnsUpdatedBatch() {
            var prb = new PatientResourceBundle("p1");
            var bwc = PatientBatchWithConsent.fromList(List.of(prb));
            var acc = new BatchDiagnosticsAcc(UUID.randomUUID(), UUID.randomUUID(), 1);

            var result = resolver.resolvePatientBatch(bwc, Map.of(), acc).block();

            assertThat(result).isNotNull();
            assertThat(result.bundles()).containsKey("p1");
        }
    }
}
