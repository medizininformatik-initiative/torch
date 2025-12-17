package de.medizininformatikinitiative.torch.service;


import de.medizininformatikinitiative.torch.consent.ConsentValidator;
import de.medizininformatikinitiative.torch.management.CompartmentManager;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttribute;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import de.medizininformatikinitiative.torch.model.management.PatientResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ReferenceWrapper;
import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroup;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceBundleLoaderTest {
    public static final String OBSERVATION_REF = "Observation/12";
    int pageCount = 4;
    @Mock
    private CompartmentManager compartmentManager;
    @Mock
    private DataStore dataStore;
    @Mock
    private ConsentValidator consentValidator;
    private ReferenceBundleLoader referenceBundleLoader;

    AnnotatedAttribute referenceAttribute = new AnnotatedAttribute("Encounter.evidence", "Encounter.evidence", true, List.of("Medication1"));
    ReferenceWrapper referenceWrapper;
    DseMappingTreeBase mappingTree = null;


    @BeforeEach
    void setup() {
        referenceBundleLoader = new ReferenceBundleLoader(compartmentManager, dataStore, consentValidator, pageCount, mappingTree);
        referenceWrapper = new ReferenceWrapper(referenceAttribute, List.of(OBSERVATION_REF), "Encounter1", "Encounter/123");
    }


    @Nested
    class TestChunkReferences {
        String REF_1 = "ref-1";
        String REF_2 = "ref-2";
        String REF_3 = "ref-3";

        @Test
        void testSingleChunk() {
            var refsPerLinkedGroup = List.of("Resource/"+REF_1, "Resource/"+REF_2, "Resource/"+REF_3);

            var chunks = referenceBundleLoader.chunkRefs(refsPerLinkedGroup, 10);

            assertThat(chunks).containsExactly(Set.of(REF_1, REF_2, REF_3));
        }

        @Test
        void withChunking() {
            var refsPerLinkedGroup = List.of("Resource/"+REF_1, "Resource/"+REF_2, "Resource/"+REF_3);

            var chunks = referenceBundleLoader.chunkRefs(refsPerLinkedGroup, 2);

            assertThat(chunks).containsExactly(
                    Set.of(REF_1, REF_2),
                    Set.of(REF_3));
        }

    }
}
