package de.medizininformatikinitiative.torch.service;

import org.hl7.fhir.r4.model.Bundle;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DataStoreHelper {
    /**
     * Builds a search batch bundle for all resources specified in {@code idsByType}.
     * <p>Creates for every resource type a searchstring with comma concatenated ids.
     *
     * @param idsByType A map from resource types to sets of ids
     * @return search batch bundle
     */
    public static Bundle createBatchBundleForReferences(Map<String, Set<String>> idsByType) {
        // Build the batch bundle
        Bundle batchBundle = new Bundle();
        batchBundle.setType(Bundle.BundleType.BATCH);
        batchBundle.getMeta().setLastUpdated(new Date());


        idsByType.forEach((type, ids) -> {
            String joinedIds = ids.stream().sorted().collect(Collectors.joining(","));
            Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
            entry.setRequest(new Bundle.BundleEntryRequestComponent()
                    .setMethod(Bundle.HTTPVerb.GET)
                    .setUrl(type + "?_id=" + joinedIds + "&_count=" + ids.size()));
            batchBundle.addEntry(entry);
        });

        return batchBundle;

    }
}
