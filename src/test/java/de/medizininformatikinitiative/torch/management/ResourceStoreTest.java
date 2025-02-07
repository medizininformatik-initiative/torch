package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.model.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashSet;
import java.util.Set;

class ResourceStoreTest {

    Patient patient1 = new Patient();
    Patient patient2 = new Patient();
    Patient patient3 = new Patient();
    ResourceGroupWrapper wrapper1;
    ResourceGroupWrapper wrapper2;
    ResourceGroupWrapper wrapper3;

    @BeforeEach
    void setUp() {
        patient1.setId("patient1");
        patient2.setId("patient2");
        patient3.setId("patient3");
        Set<AnnotatedAttributeGroup> attributeGroups = new HashSet<>();
        wrapper1 = new ResourceGroupWrapper(patient1, attributeGroups);
        wrapper2 = new ResourceGroupWrapper(patient2, attributeGroups);
        wrapper3 = new ResourceGroupWrapper(patient3, attributeGroups);
    }

    @Test
    void getMatch() {
        ResourceStore cache = new ResourceStore();
        cache.put(wrapper1);

        Mono<ResourceGroupWrapper> result = cache.get(patient1.getId());

        StepVerifier.create(result)
                .expectNext(wrapper1)
                .verifyComplete();
    }

    @Test
    void getEmpty() {
        ResourceStore cache = new ResourceStore();

        Mono<?> result = cache.get(patient1.getId());

        StepVerifier.create(result)
                .verifyComplete();  // Ensures Mono is empty
    }

    @Test
    void put() {
        ResourceStore cache = new ResourceStore();
        cache.put(wrapper1);

        Mono<ResourceGroupWrapper> result = cache.get(patient1.getId());

        StepVerifier.create(result)
                .expectNext(wrapper1)
                .verifyComplete();
    }

    @Test
    void delete() {
        ResourceStore cache = new ResourceStore();
        cache.put(wrapper1);
        cache.delete(patient1.getId());

        Mono<?> result = cache.get(patient1.getId());

        StepVerifier.create(result)
                .verifyComplete(); // Should be empty after invalidation
    }

}
