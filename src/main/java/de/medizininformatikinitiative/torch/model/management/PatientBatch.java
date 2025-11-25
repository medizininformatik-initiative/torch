package de.medizininformatikinitiative.torch.model.management;

import de.medizininformatikinitiative.torch.model.fhir.QueryParams;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static de.medizininformatikinitiative.torch.model.fhir.QueryParams.multiStringValue;
import static java.util.Objects.requireNonNull;

public record PatientBatch(List<String> ids, UUID batchId) {

    public PatientBatch {
        ids = List.copyOf(ids);
        requireNonNull(batchId);
    }

    public PatientBatch(List<String> ids) {
        this(ids, UUID.randomUUID());
    }

    public static PatientBatch of(String... ids) {
        return new PatientBatch(List.of(ids));
    }

    public static PatientBatch of(List<String> ids) {
        return new PatientBatch(ids);
    }

    /**
     * Splits a list of strings into smaller batches of a specified size.
     *
     * @param batchSize the maximum size of each batch
     * @return a list of lists, where each sublist is a batch of the original list
     */
    public List<PatientBatch> split(int batchSize) {
        List<PatientBatch> batches = new ArrayList<>();
        int totalSize = ids.size();

        for (int i = 0; i < totalSize; i += batchSize) {
            int end = Math.min(totalSize, i + batchSize);
            batches.add(new PatientBatch(ids.subList(i, end)));
        }
        return List.copyOf(batches);
    }

    public boolean isEmpty() {
        return ids.isEmpty();
    }

    /**
     * Returns a search param which selects all resources of Compartment of patients defined in this batch.
     *
     * @param resourceType FHIR resource type to be searched for
     * @return Search Param
     */
    public QueryParams compartmentSearchParam(String resourceType) {
        if ("Patient".equals(resourceType)) {
            return QueryParams.of("_id", multiStringValue(ids));
        } else {
            return QueryParams.of("patient", multiStringValue(ids.stream().map(id -> "Patient/" + id).toList()));
        }
    }
}
