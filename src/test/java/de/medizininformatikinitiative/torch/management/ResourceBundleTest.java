package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.model.management.ResourceBundle;
import de.medizininformatikinitiative.torch.model.management.ResourceGroupWrapper;
import de.medizininformatikinitiative.torch.util.ResourceUtils;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceBundleTest {

    Patient patient1 = new Patient();
    Patient patient2 = new Patient();
    Patient patient3 = new Patient();
    ResourceGroupWrapper wrapper1;
    ResourceGroupWrapper wrapper2;
    ResourceGroupWrapper wrapper3;
    ResourceGroupWrapper wrapper1Mod;
    String id;

    @BeforeEach
    void setUp() {
        patient1.setId("patient1");
        patient2.setId("patient2");
        patient3.setId("patient3");
        Set<String> attributeGroups1 = Set.of("group1", "group2");
        Set<String> attributeGroups2 = Set.of("group3");
        wrapper1 = new ResourceGroupWrapper(patient1, attributeGroups1);
        wrapper2 = new ResourceGroupWrapper(patient2, attributeGroups1);
        wrapper3 = new ResourceGroupWrapper(patient3, attributeGroups1);
        wrapper1Mod = new ResourceGroupWrapper(patient1, attributeGroups2);
        id = ResourceUtils.getRelativeURL(patient1);
    }

    @Nested
    class RetrievalTests {
        @Test
        void getMatch() {
            ResourceBundle cache = new ResourceBundle();
            cache.mergingPut(wrapper1);

            Mono<ResourceGroupWrapper> result = cache.get(id);

            StepVerifier.create(result)
                    .expectNext(wrapper1)
                    .verifyComplete();
        }

        @Test
        void isEmpty() {
            ResourceBundle cache = new ResourceBundle();
            Mono<?> result = cache.get(id);
            StepVerifier.create(result).verifyComplete();
        }
    }

    @Nested
    class OverwritingPutTests {
        @Test
        void overwritingPutAddsNewEntry() {
            ResourceBundle cache = new ResourceBundle();
            assertThat(cache.overwritingPut(wrapper1)).isTrue();

            Mono<ResourceGroupWrapper> result = cache.get(id);
            StepVerifier.create(result)
                    .expectNext(wrapper1)
                    .verifyComplete();
        }

        @Test
        void overwritingPutWithSameGroupsDoesNotUpdate() {
            ResourceBundle cache = new ResourceBundle();
            cache.overwritingPut(wrapper1);
            assertThat(cache.overwritingPut(wrapper1)).isFalse();
        }

        @Test
        void overwritingPutWithDifferentGroupsUpdates() {
            ResourceBundle cache = new ResourceBundle();
            cache.overwritingPut(wrapper1);
            assertThat(cache.overwritingPut(wrapper1Mod)).isTrue();

            Mono<ResourceGroupWrapper> result = cache.get(id);
            StepVerifier.create(result)
                    .expectNext(wrapper1Mod)
                    .verifyComplete();
        }
    }

    @Nested
    class MergingPutTests {
        @Test
        void mergingPutAddsNewEntry() {
            ResourceBundle cache = new ResourceBundle();
            assertThat(cache.mergingPut(wrapper1)).isTrue();
        }

        @Test
        void mergingPutDoesNotAddDuplicateEntry() {
            ResourceBundle cache = new ResourceBundle();
            cache.mergingPut(wrapper1);
            assertThat(cache.mergingPut(wrapper1)).isFalse();
        }

        @Test
        void mergingPutMergesGroups() {
            ResourceBundle cache = new ResourceBundle();
            cache.mergingPut(wrapper1);
            cache.mergingPut(wrapper1Mod);

            Set<String> mergedGroups = new HashSet<>(wrapper1.groupSet());
            mergedGroups.addAll(wrapper1Mod.groupSet());
            ResourceGroupWrapper expected = new ResourceGroupWrapper(patient1, mergedGroups);

            Mono<ResourceGroupWrapper> result = cache.get(id);
            StepVerifier.create(result)
                    .expectNext(expected)
                    .verifyComplete();
        }
    }

    @Nested
    class RemovalTests {
        @Test
        void removeExistingEntry() {
            ResourceBundle cache = new ResourceBundle();
            cache.mergingPut(wrapper1);
            cache.remove(id);

            Mono<?> result = cache.get(id);
            StepVerifier.create(result).verifyComplete();
        }
    }
}