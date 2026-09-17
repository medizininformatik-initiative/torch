package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.TargetClassCreationException;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.BatchExclusions;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.ResourceExclusionReason;
import de.medizininformatikinitiative.torch.exceptions.RedactionException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.ResourceExclusionEvent;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionPatientBatch;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import de.medizininformatikinitiative.torch.model.extraction.ResourceExtractionInfo;
import de.medizininformatikinitiative.torch.model.management.CopyTreeNode;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class BatchCopierRedacterTest {

    @Mock
    private ElementCopier copier;

    @Mock
    private Redaction redaction;

    @InjectMocks
    private BatchCopierRedacter transformer;

    private ExtractionResourceBundle extractionBundle;
    private Resource resource;

    static Stream<Class<? extends Exception>> easyExceptionProvider() {
        return Stream.of(
                RedactionException.class,
                ReflectiveOperationException.class
        );
    }

    @BeforeEach
    void setUp() throws RedactionException {
        MockitoAnnotations.openMocks(this);

        transformer = spy(transformer);

        resource = new Patient();
        resource.setId("dummy");

        // Set up bundle with exactly one resource and its info
        Map<ExtractionId, ResourceExtractionInfo> infoMap = Map.of(
                ExtractionId.fromRelativeUrl("Patient/dummy"),
                new ResourceExtractionInfo(
                        Set.of("G1"),
                        Map.of() // no references needed for this test
                )
        );
        ConcurrentHashMap<ExtractionId, Optional<Resource>> cache = new ConcurrentHashMap<>();
        cache.put(ExtractionId.fromRelativeUrl("Patient/dummy"), Optional.of(resource));

        extractionBundle = new ExtractionResourceBundle(new ConcurrentHashMap<>(infoMap), cache);

        // group map stub not needed deeply
        // but createWrapper must not run real logic
        doReturn(mock(ExtractionRedactionWrapper.class))
                .when(transformer)
                .createWrapper(any(), any(), any());
    }

    @ParameterizedTest
    @MethodSource("easyExceptionProvider")
    void transformBundle_removesResourceOnEasyException(Class<? extends Exception> exClass) throws Exception {
        Exception ex = exClass.getConstructor(String.class).newInstance("fail");

        doThrow(ex)
                .when(transformer)
                .transformResource(any());

        BatchExclusions exclusions = BatchExclusions.empty();
        transformer.transformBundle(extractionBundle, Map.of(), exclusions, "pat-1");

        assertThat(extractionBundle.getResource(ExtractionId.fromRelativeUrl("Patient/dummy"))).isEmpty();
        assertThat(exclusions.getResourceExclusions()).singleElement().satisfies(event -> {
            assertThat(event.reason()).isEqualTo(ResourceExclusionReason.REDACTION_FAILURE);
            assertThat(event.groupId()).isEqualTo("G1");
            assertThat(event.resourceId()).isEqualTo("Patient/dummy");
            assertThat(event.patientId()).isEqualTo("pat-1");
        });
    }

    @org.junit.jupiter.api.Test
    void transformBundle_removesResourceOnTargetClassCreationException() throws Exception {

        TargetClassCreationException ex =
                new TargetClassCreationException(ExtractionRedactionWrapper.class);

        doThrow(ex)
                .when(transformer)
                .transformResource(any());

        BatchExclusions exclusions = BatchExclusions.empty();
        transformer.transformBundle(extractionBundle, Map.of(), exclusions);

        assertThat(extractionBundle.getResource(ExtractionId.fromRelativeUrl("Patient/dummy"))).isEmpty();
        assertThat(exclusions.getResourceExclusions()).singleElement().satisfies(event -> {
            assertThat(event.reason()).isEqualTo(ResourceExclusionReason.REDACTION_FAILURE);
            assertThat(event.groupId()).isEqualTo("G1");
            assertThat(event.resourceId()).isEqualTo("Patient/dummy");
            assertThat(event.patientId()).isEmpty();
        });
    }

    @Test
    void transformBundle_propagatesUnexpectedRuntimeException() throws Exception {
        doThrow(new NullPointerException("bug"))
                .when(transformer)
                .transformResource(any());

        BatchExclusions exclusions = BatchExclusions.empty();
        assertThatThrownBy(() -> transformer.transformBundle(extractionBundle, Map.of(), exclusions))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("bug");

        assertThat(extractionBundle.getResource(ExtractionId.fromRelativeUrl("Patient/dummy"))).isPresent();
        assertThat(exclusions.isEmpty()).isTrue();
    }

    @Test
    void transformBatch_threadsPatientIdFromBundleMapKey() throws Exception {
        doThrow(new RedactionException("fail"))
                .when(transformer)
                .transformResource(any());

        ExtractionId idA = ExtractionId.fromRelativeUrl("Patient/a");
        Resource resourceA = new Patient();
        resourceA.setId("a");
        ConcurrentHashMap<ExtractionId, Optional<Resource>> cacheA = new ConcurrentHashMap<>();
        cacheA.put(idA, Optional.of(resourceA));
        ExtractionResourceBundle bundleA = new ExtractionResourceBundle(
                new ConcurrentHashMap<>(Map.of(idA, new ResourceExtractionInfo(Set.of("G1"), Map.of()))), cacheA);

        ExtractionId idB = ExtractionId.fromRelativeUrl("Patient/b");
        Resource resourceB = new Patient();
        resourceB.setId("b");
        ConcurrentHashMap<ExtractionId, Optional<Resource>> cacheB = new ConcurrentHashMap<>();
        cacheB.put(idB, Optional.of(resourceB));
        ExtractionResourceBundle bundleB = new ExtractionResourceBundle(
                new ConcurrentHashMap<>(Map.of(idB, new ResourceExtractionInfo(Set.of("G1"), Map.of()))), cacheB);

        ExtractionPatientBatch batch = new ExtractionPatientBatch(Map.of("pat-a", bundleA, "pat-b", bundleB), UUID.randomUUID());

        BatchExclusions exclusions = BatchExclusions.empty();
        transformer.transformBatch(batch, Map.of(), exclusions);

        assertThat(exclusions.getResourceExclusions())
                .extracting(ResourceExclusionEvent::patientId)
                .containsExactlyInAnyOrder("pat-a", "pat-b");
    }

    @Nested
    class CreateWrapper {

        private BatchCopierRedacter real;

        @BeforeEach
        void setUpReal() {
            real = new BatchCopierRedacter(copier, redaction);
        }

        @Test
        void singleGroup_buildsWrapperWithProfile() throws RedactionException {
            var patient = new Patient();
            patient.setId("p1");
            var group = new AnnotatedAttributeGroup("G1", "Patient", "http://profile/Patient",
                    List.of(new AnnotatedAttribute("Patient.id", "Patient.id", false)), List.of());
            var info = new ResourceExtractionInfo(Set.of("G1"), Map.of());

            var wrapper = real.createWrapper(patient, info, Map.of("G1", group));

            assertThat(wrapper.resource()).isSameAs(patient);
            assertThat(wrapper.profiles()).containsExactly("http://profile/Patient");
        }

        @Test
        void unknownGroup_skippedGracefully() throws RedactionException {
            var patient = new Patient();
            patient.setId("p1");
            var info = new ResourceExtractionInfo(Set.of("unknown-group"), Map.of());

            var wrapper = real.createWrapper(patient, info, Map.of());

            assertThat(wrapper.resource()).isSameAs(patient);
            assertThat(wrapper.profiles()).isEmpty();
        }

        @Test
        void multipleGroups_mergesProfiles() throws RedactionException {
            var patient = new Patient();
            patient.setId("p1");
            var g1 = new AnnotatedAttributeGroup("G1", "Patient", "http://profile/P1",
                    List.of(new AnnotatedAttribute("Patient.id", "Patient.id", false)), List.of());
            var g2 = new AnnotatedAttributeGroup("G2", "Patient", "http://profile/P2",
                    List.of(new AnnotatedAttribute("Patient.name", "Patient.name", false)), List.of());
            var info = new ResourceExtractionInfo(Set.of("G1", "G2"), Map.of());

            var wrapper = real.createWrapper(patient, info, Map.of("G1", g1, "G2", g2));

            assertThat(wrapper.profiles()).containsExactlyInAnyOrder("http://profile/P1", "http://profile/P2");
        }
    }

    @Nested
    class TransformResource {

        @Test
        void returnsTransformedResource() throws Exception {
            var patient = new Patient();
            patient.setId("dummy");
            var wrapper = new ExtractionRedactionWrapper(patient, Set.of(), Map.of(), new CopyTreeNode("Patient"));

            var result = transformer.transformResource(wrapper);

            assertThat(result).isNotNull().isInstanceOf(Patient.class);
        }

        @Test
        void reValidatesProfilesAgainstCopiedResource() {
            var withRealCopier = new BatchCopierRedacter(new ElementCopier(FhirContext.forR4()), redaction);

            Condition condition = new Condition();
            condition.setId("c1");
            Meta meta = new Meta();
            meta.addProfile("http://example.org/profile");
            condition.setMeta(meta);

            // Empty copy tree: the copy step won't carry meta.profile onto the target resource,
            // so the post-copy wrapper must catch the missing association even though the source was valid.
            CopyTreeNode copyTreeWithoutMetaProfile = new CopyTreeNode("Condition");

            assertThatThrownBy(() -> {
                var wrapper = ExtractionRedactionWrapper.of(condition, Set.of("http://example.org/profile"), Map.of(), copyTreeWithoutMetaProfile);
                withRealCopier.transformResource(wrapper);
            }).isInstanceOf(RedactionException.class);
        }
    }
}
