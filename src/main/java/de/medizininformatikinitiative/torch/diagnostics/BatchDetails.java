package de.medizininformatikinitiative.torch.diagnostics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records different measurements during the processing of a single batch.
 *
 * @param nanosElapsed      the amount of nanoseconds elapsed at each stage
 * @param numCohortPatients the amount of patients in the original cohort of this batch before extraction (i.e. before any exclusions)
 * @param numFinalPatients  the amount of patients in this batch after extraction (i.e. after exclusions could have occurred)
 */
public record BatchDetails(Map<PipelineStage, Long> nanosElapsed, int numCohortPatients, int numFinalPatients) {

    public static BatchDetails empty() {
        return new BatchDetails(new ConcurrentHashMap<>(), 0, 0);
    }

    public BatchDetails setNumCohortPatients(int numCohortPatients) {
        return new BatchDetails(nanosElapsed, numCohortPatients, numFinalPatients);
    }

    public BatchDetails setFinalPatientCount(int numFinalPatients) {
        return new BatchDetails(nanosElapsed, numCohortPatients, numFinalPatients);
    }
}
