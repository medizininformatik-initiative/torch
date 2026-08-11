package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.TargetClassCreationException;
import de.medizininformatikinitiative.torch.exceptions.RedactionException;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.extraction.ExtractionId;
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
        void singleGroup_buildsWrapperWithProfile() throws RedactionException {
            var patient = new Patient();
            patient.setId("p1");
            var group = new AnnotatedAttributeGroup("G1", "Patient", "http://profile/Patient",
                    List.of(new AnnotatedAttribute("Patient.id", "Patient.id", false)), List.of());
            var info = new ResourceExtractionInfo(Set.of("G1"), Map.of());

            var wrapper = real.createWrapper(patient, info, Map.of("G1", group), Map.of());

            assertThat(wrapper.resource()).isSameAs(patient);
            assertThat(wrapper.profiles()).containsExactly("http://profile/Patient");
        }

        /**
         * The wrapper's {@code referenceResolver} must delegate to the {@code referenceLookup} snapshot passed
         * into {@code createWrapper}, for both present and absent ids.
         */
        @Test
        void referenceResolver_delegatesToProvidedLookup() throws RedactionException {
            var patient = new Patient();
            patient.setId("p1");
            var info = new ResourceExtractionInfo(Set.of(), Map.of());
            var otherPatient = new Patient();
            otherPatient.setId("other");
            var referenceLookup = Map.of(ExtractionId.fromRelativeUrl("Patient/other"), Optional.<Resource>of(otherPatient));

            var wrapper = real.createWrapper(patient, info, Map.of(), referenceLookup);

            assertThat(wrapper.referenceResolver().apply(ExtractionId.fromRelativeUrl("Patient/other"))).contains(otherPatient);
            assertThat(wrapper.referenceResolver().apply(ExtractionId.fromRelativeUrl("Patient/missing"))).isEmpty();
        }

        @Test
        void unknownGroup_skippedGracefully() throws RedactionException {
            var patient = new Patient();
            patient.setId("p1");
            var info = new ResourceExtractionInfo(Set.of("unknown-group"), Map.of());

            var wrapper = real.createWrapper(patient, info, Map.of(), Map.of());

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
