package de.medizininformatikinitiative.torch.service;

import de.medizininformatikinitiative.torch.TargetClassCreationException;
import de.medizininformatikinitiative.torch.exceptions.RedactionException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionPatientBatch;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionResourceBundle;
import de.medizininformatikinitiative.torch.model.extraction.IdentifierReference;
import de.medizininformatikinitiative.torch.model.extraction.ResourceExtractionInfo;
import de.medizininformatikinitiative.torch.model.management.CopyTreeNode;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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
    void setUp() {
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
                .createWrapper(any(), any(), any(), any());
    }

    @ParameterizedTest
    @MethodSource("easyExceptionProvider")
    void transformBundle_removesResourceOnEasyException(Class<? extends Exception> exClass) throws Exception {
        Exception ex = exClass.getConstructor(String.class).newInstance("fail");

        doThrow(ex)
                .when(transformer)
                .transformResource(any());

        transformer.transformBundle(extractionBundle, Map.of());

        assertThat(extractionBundle.getResource(ExtractionId.fromRelativeUrl("Patient/dummy"))).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void transformBundle_removesResourceOnTargetClassCreationException() throws Exception {

        TargetClassCreationException ex =
                new TargetClassCreationException(ExtractionRedactionWrapper.class);

        doThrow(ex)
                .when(transformer)
                .transformResource(any());

        transformer.transformBundle(extractionBundle, Map.of());

        assertThat(extractionBundle.getResource(ExtractionId.fromRelativeUrl("Patient/dummy"))).isEmpty();
    }

    @Test
    void transformBundle_propagatesUnexpectedRuntimeException() throws Exception {
        doThrow(new NullPointerException("bug"))
                .when(transformer)
                .transformResource(any());

        assertThatThrownBy(() -> transformer.transformBundle(extractionBundle, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("bug");

        assertThat(extractionBundle.getResource(ExtractionId.fromRelativeUrl("Patient/dummy"))).isPresent();
    }

    @Nested
    class CreateWrapper {

        private BatchCopierRedacter real;

        @BeforeEach
        void setUpReal() {
            real = new BatchCopierRedacter(copier, redaction);
        }

        @Test
        void singleGroup_buildsWrapperWithProfile() {
            var patient = new Patient();
            patient.setId("p1");
            var group = new AnnotatedAttributeGroup("G1", "Patient", "http://profile/Patient",
                    List.of(new AnnotatedAttribute("Patient.id", "Patient.id", false)), List.of());
            var info = new ResourceExtractionInfo(Set.of("G1"), Map.of());

            var wrapper = real.createWrapper(patient, info, Map.of("G1", group), Map.of());

            assertThat(wrapper.resource()).isSameAs(patient);
            assertThat(wrapper.profiles()).containsExactly("http://profile/Patient");
        }

        @Test
        void unknownGroup_skippedGracefully() {
            var patient = new Patient();
            patient.setId("p1");
            var info = new ResourceExtractionInfo(Set.of("unknown-group"), Map.of());

            var wrapper = real.createWrapper(patient, info, Map.of(), Map.of());

            assertThat(wrapper.resource()).isSameAs(patient);
            assertThat(wrapper.profiles()).isEmpty();
        }

        @Test
        void multipleGroups_mergesProfiles() {
            var patient = new Patient();
            patient.setId("p1");
            var g1 = new AnnotatedAttributeGroup("G1", "Patient", "http://profile/P1",
                    List.of(new AnnotatedAttribute("Patient.id", "Patient.id", false)), List.of());
            var g2 = new AnnotatedAttributeGroup("G2", "Patient", "http://profile/P2",
                    List.of(new AnnotatedAttribute("Patient.name", "Patient.name", false)), List.of());
            var info = new ResourceExtractionInfo(Set.of("G1", "G2"), Map.of());

            var wrapper = real.createWrapper(patient, info, Map.of("G1", g1, "G2", g2), Map.of());

            assertThat(wrapper.profiles()).containsExactlyInAnyOrder("http://profile/P1", "http://profile/P2");
        }
    }

    @Nested
    class TransformResource {

        @Test
        void returnsTransformedResource() throws Exception {
            var patient = new Patient();
            patient.setId("dummy");
            var wrapper = new ExtractionRedactionWrapper(patient, Set.of(), Map.of(), new CopyTreeNode("Patient"), Map.of());

            var result = transformer.transformResource(wrapper);

            assertThat(result).isNotNull().isInstanceOf(Patient.class);
        }
    }

    @Nested
    class BuildIdentifierIndex {

        private BatchCopierRedacter real;

        @BeforeEach
        void setUpReal() {
            real = new BatchCopierRedacter(copier, redaction);
        }

        /**
         * A patient resource may hold an identifier-only reference to a resource that lives only in the batch's
         * shared core bundle (not in that patient's own bundle) — e.g. a reference to a core resource. The index
         * has to span both, or such a reference would incorrectly appear unresolvable at redaction time.
         */
        @Test
        void indexesResourcesFromBothPatientAndCoreBundles() {
            var patientOnlyResource = new Patient();
            patientOnlyResource.setId("Patient/p1");
            patientOnlyResource.addIdentifier(new Identifier().setSystem("http://system").setValue("patient-val"));

            var coreOnlyResource = new Patient();
            coreOnlyResource.setId("Patient/core-1");
            coreOnlyResource.addIdentifier(new Identifier().setSystem("http://system").setValue("core-val"));

            var patientBundleCache = new ConcurrentHashMap<ExtractionId, Optional<Resource>>();
            patientBundleCache.put(ExtractionId.fromRelativeUrl("Patient/p1"), Optional.of(patientOnlyResource));
            var patientBundle = new ExtractionResourceBundle(new ConcurrentHashMap<>(), patientBundleCache);

            var coreBundleCache = new ConcurrentHashMap<ExtractionId, Optional<Resource>>();
            coreBundleCache.put(ExtractionId.fromRelativeUrl("Patient/core-1"), Optional.of(coreOnlyResource));
            var coreBundle = new ExtractionResourceBundle(new ConcurrentHashMap<>(), coreBundleCache);

            var batch = new ExtractionPatientBatch(Map.of("p1", patientBundle), coreBundle, UUID.randomUUID());

            var index = real.buildIdentifierIndex(batch);

            assertThat(index).containsEntry(new IdentifierReference("http://system", "patient-val"),
                    Set.of(ExtractionId.fromRelativeUrl("Patient/p1")));
            assertThat(index).containsEntry(new IdentifierReference("http://system", "core-val"),
                    Set.of(ExtractionId.fromRelativeUrl("Patient/core-1")));
        }
    }
}
