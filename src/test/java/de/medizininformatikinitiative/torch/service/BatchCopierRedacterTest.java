package de.medizininformatikinitiative.torch.service;

import ca.uhn.fhir.context.FhirContext;
import de.medizininformatikinitiative.torch.management.ConsentHandler;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.ExtractionRedactionWrapper;
import de.medizininformatikinitiative.torch.model.management.ProfileAttributeCollection;
import de.medizininformatikinitiative.torch.model.management.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import de.medizininformatikinitiative.torch.setup.IntegrationTestSetup;
import de.medizininformatikinitiative.torch.util.ElementCopier;
import de.medizininformatikinitiative.torch.util.Redaction;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class) // Enables Mockito support
class BatchCopierRedacterTest {


    private static final IntegrationTestSetup INTEGRATION_TEST_SETUP = new IntegrationTestSetup();
    public static final String OBSERVATION = "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab";
    @Mock
    DataStore dataStore;
    @Mock
    ConsentHandler handler;
    @Mock
    ElementCopier copier;
    @Mock
    Redaction redaction;
    @Mock
    FhirContext context;
    @Mock
    DseMappingTreeBase dseMappingTreeBase;

    Map<String, AnnotatedAttributeGroup> groupMap = new HashMap<>();


    AnnotatedAttribute effective = new AnnotatedAttribute("Observation.effective", "Observation.effective", "Observation.effective", false);
    AnnotatedAttribute metaAttribute = new AnnotatedAttribute("Observation.meta", "Observation.meta", "Observation.meta", false);
    AnnotatedAttribute id = new AnnotatedAttribute("Observation.id", "Observation.id", "Observation.id", false);
    AnnotatedAttribute subject = new AnnotatedAttribute("Observation.subject", "Observation.subject", "Observation.subject", false);
    ExtractionRedactionWrapper extractionWrapper;


    @InjectMocks
    BatchCopierRedacter transformer;
    private ResourceGroupWrapper wrapper;

    @Nested
    class PathHelperTest {
        @Test
        void testIsParentPath() {
            assertThat(transformer.isParentPath("Patient.identifier", "Patient.identifier:GKV")).isTrue();
            assertThat(transformer.isParentPath("Patient.identifier", "Patient.identifier.system")).isTrue();
            assertThat(transformer.isParentPath("Observation.value[x]", "Observation.value[x]:valueQuantity")).isTrue();
            assertThat(transformer.isParentPath("Patient", "Patient.identifier")).isTrue();

            assertThat(transformer.isParentPath("Patient.identifier", "Observation.value")).isFalse();
            assertThat(transformer.isParentPath("Patient.identifier", "Patient.identifierExtra")).isFalse();
        }

        @Test
        void testFindChildren() {
            Set<String> existingPaths = new HashSet<>();
            existingPaths.add("Patient.identifier");
            existingPaths.add("Patient.identifier:GKV");
            existingPaths.add("Patient.identifier.system");
            existingPaths.add("Observation.value[x]");
            existingPaths.add("Observation.value[x]:valueQuantity");

            Set<String> children = transformer.findChildren("Patient.identifier", existingPaths);
            assertThat(children).containsExactlyInAnyOrder("Patient.identifier:GKV", "Patient.identifier.system");

            Set<String> observationChildren = transformer.findChildren("Observation.value[x]", existingPaths);
            assertThat(observationChildren).containsExactly("Observation.value[x]:valueQuantity");

            Set<String> noChildren = transformer.findChildren("MedicationRequest", existingPaths);
            assertThat(noChildren).isEmpty();
        }


    }


    @Nested
    class Transform {
        Observation src;

        @BeforeEach
        void setUp() {
            src = new Observation();
            src.setId("123");
            Meta meta = new Meta();
            meta.setProfile(List.of(new CanonicalType(OBSERVATION)));
            src.setSubject(new Reference("Patient/123"));


            extractionWrapper = new ExtractionRedactionWrapper(src, Set.of(), Map.of(), Set.of(effective, metaAttribute, id, subject));
        }


        @Test
        void successAttributeCopy() throws Exception {


            Resource result = transformer.transform(extractionWrapper);

            Mockito.verify(copier).copy(eq(src), any(), eq(effective));
            Mockito.verify(copier).copy(eq(src), any(), eq(metaAttribute));
            Mockito.verify(copier).copy(eq(src), any(), eq(id));
            Mockito.verify(copier).copy(eq(src), any(), eq(subject));


        }
    }

    @Nested
    class CollectHighestLevelAttributes {
        @Mock
        private AnnotatedAttributeGroup mockGroup1;

        @Mock
        private AnnotatedAttributeGroup mockGroup2;

        @Mock
        private AnnotatedAttribute mockAttribute1;
        @Mock
        private AnnotatedAttribute mockAttribute2;
        @Mock
        private AnnotatedAttribute mockAttribute3;
        @Mock
        private AnnotatedAttribute mockAttribute4;

        @BeforeEach
        void setUp() {
        }

        @Test
        void testCollectHighestLevelAttributes() {
            Set<String> groups = Set.of("group1", "group2");

            when(mockAttribute1.attributeRef()).thenReturn("Patient.identifier");
            when(mockAttribute2.attributeRef()).thenReturn("Patient.name:GKV");
            when(mockAttribute3.attributeRef()).thenReturn("Patient.name");
            when(mockAttribute4.attributeRef()).thenReturn("Patient.identifier.system");

            List<AnnotatedAttribute> attributes1 = List.of(mockAttribute1, mockAttribute2);
            List<AnnotatedAttribute> attributes2 = List.of(mockAttribute3, mockAttribute4);
            when(mockGroup1.attributes()).thenReturn(attributes1);
            when(mockGroup2.attributes()).thenReturn(attributes2);
            when(mockGroup1.groupReference()).thenReturn("Profile1");
            when(mockGroup2.groupReference()).thenReturn("Profile2");


            Map<String, AnnotatedAttributeGroup> groupMap = new HashMap<>();
            groupMap.put("group1", mockGroup1);
            groupMap.put("group2", mockGroup2);

            ProfileAttributeCollection result = transformer.collectHighestLevelAttributes(groupMap, groups);

            // Expected: "Patient.identifier" should exclude "Patient.identifier.system"
            assertThat(result.attributes()).containsExactlyInAnyOrder(mockAttribute1, mockAttribute3);
            assertThat(result.profiles()).containsExactlyInAnyOrder("Profile1", "Profile2");
        }


    }


}
