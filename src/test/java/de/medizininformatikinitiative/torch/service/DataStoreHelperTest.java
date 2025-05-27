package de.medizininformatikinitiative.torch.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static de.medizininformatikinitiative.torch.assertions.Assertions.assertThat;

class DataStoreHelperTest {

    @Test
    void createBatchBundleForReferences() {
        Map<String, Set<String>> referencesGroupedByType = Map.of(
                "Observation", Set.of("123"),
                "Patient", Set.of("2", "1", "3"),
                "Medication", Set.of("123"));

        assertThat(DataStoreHelper.createBatchBundleForReferences(referencesGroupedByType)).extractBatchBundleUrls()
                .containsExactlyInAnyOrder(
                        "Patient?_id=1,2,3&_count=3", "Observation?_id=123&_count=1", "Medication?_id=123&_count=1"
                );
    }

}
