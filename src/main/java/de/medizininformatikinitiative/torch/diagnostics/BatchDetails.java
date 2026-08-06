package de.medizininformatikinitiative.torch.diagnostics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records different measurements during the processing of a single batch.
 *
 * @param nanosElapsed        the amount of nanoseconds elapsed at each stage
 * @param numCohortPatients   the amount of patients in the original cohort of this batch before extraction (i.e. before any exclusions)
 * @param numFinalPatients    the amount of patients in this batch after extraction (i.e. after exclusions could have occurred)
 * @param resourceInclusions  the amount of resources that successfully completed extraction, per AttributeGroup-ID
 */
public record BatchDetails(Map<PipelineStage, Long> nanosElapsed, int numCohortPatients, int numFinalPatients,
                           Map<String, Integer> resourceInclusions) {

    public static BatchDetails empty() {
        return new BatchDetails(new ConcurrentHashMap<>(), 0, 0, new ConcurrentHashMap<>());
    }

    public BatchDetails setNumCohortPatients(int numCohortPatients) {
        return new BatchDetails(nanosElapsed, numCohortPatients, numFinalPatients, resourceInclusions);
    }

    public BatchDetails setFinalPatientCount(int numFinalPatients) {
        return new BatchDetails(nanosElapsed, numCohortPatients, numFinalPatients, resourceInclusions);
    }
}
