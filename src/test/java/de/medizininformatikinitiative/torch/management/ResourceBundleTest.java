package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.model.ResourceBundle;
import de.medizininformatikinitiative.torch.model.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.model.crtdl.annotated.AnnotatedAttributeGroup;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

class ResourceBundleTest {

    Patient patient1 = new Patient();
    Patient patient2 = new Patient();
    Patient patient3 = new Patient();
    ResourceGroupWrapper wrapper1;
    ResourceGroupWrapper wrapper2;
    ResourceGroupWrapper wrapper3;
    ResourceGroupWrapper wrapper1Mod;
    ResourceGroupWrapper wrapper1MergeResult;

    @BeforeEach
    void setUp() {
        patient1.setId("patient1");
        patient2.setId("patient2");
        patient3.setId("patient3");
        Set<AnnotatedAttributeGroup> attributeGroups1 = new HashSet<>();
        attributeGroups1.add(new AnnotatedAttributeGroup("group1", "12345", "reference", List.of(), List.of(), false));
        attributeGroups1.add(new AnnotatedAttributeGroup("group2", "123", "reference", List.of(), List.of(), false));

        Set<AnnotatedAttributeGroup> attributeGroups2 = new HashSet<>();
        attributeGroups2.add(new AnnotatedAttributeGroup("group3", "1234", "reference2", List.of(), List.of(), false));

        wrapper1 = new ResourceGroupWrapper(patient1, attributeGroups1);
        wrapper2 = new ResourceGroupWrapper(patient2, attributeGroups1);
        wrapper3 = new ResourceGroupWrapper(patient3, attributeGroups1);
        wrapper1Mod = new ResourceGroupWrapper(patient1, attributeGroups2);

        Set<AnnotatedAttributeGroup> mergedAttributeGroups = new HashSet<>(attributeGroups1);
        mergedAttributeGroups.addAll(attributeGroups2);
        wrapper1MergeResult = new ResourceGroupWrapper(patient1, mergedAttributeGroups);
    }

    @Test
    void getMatch() {
        ResourceBundle cache = new ResourceBundle();
        cache.put(wrapper1);

        Mono<ResourceGroupWrapper> result = cache.get(patient1.getId());

        StepVerifier.create(result)
                .expectNext(wrapper1)
                .verifyComplete();
    }

    @Test
    void isEmpty() {
        ResourceBundle cache = new ResourceBundle();

        Mono<?> result = cache.get(patient1.getId());

        StepVerifier.create(result)
                .verifyComplete();  // Ensures Mono is empty
    }

    @Test
    void put() {
        ResourceBundle cache = new ResourceBundle();
        cache.put(wrapper1);

        Mono<ResourceGroupWrapper> result = cache.get(patient1.getId());

        StepVerifier.create(result)
                .expectNext(wrapper1)
                .verifyComplete();
    }

    @Test
    void putMerge() {
        ResourceBundle cache = new ResourceBundle();
        cache.put(wrapper1);
        cache.put(wrapper1Mod);

        Mono<ResourceGroupWrapper> result = cache.get(patient1.getId());

        StepVerifier.create(result)
                .expectNext(wrapper1MergeResult)
                .verifyComplete();
    }


    @Test
    void delete() {
        ResourceBundle cache = new ResourceBundle();
        cache.put(wrapper1);
        cache.delete(patient1.getId());

        Mono<?> result = cache.get(patient1.getId());

        StepVerifier.create(result)
                .verifyComplete(); // Should be empty after invalidation
    }

}
