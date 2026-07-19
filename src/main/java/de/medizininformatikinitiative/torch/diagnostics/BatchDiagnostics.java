package de.medizininformatikinitiative.torch.diagnostics;

import de.medizininformatikinitiative.torch.diagnostics.exclusions.BatchExclusions;
import static java.util.Objects.requireNonNull;

/**
 * Holds various diagnostics that are recorded during the processing of a single batch.
 *
 * @param batchExclusions   the exclusion events happening during processing
 * @param batchDetails      other measurements recorded during processing
 */
public record BatchDiagnostics(BatchExclusions batchExclusions, BatchDetails batchDetails) {

    public BatchDiagnostics {
        requireNonNull(batchDetails);
        requireNonNull(batchExclusions);
    }

    public static BatchDiagnostics empty() {
        return new BatchDiagnostics(BatchExclusions.empty(), BatchDetails.empty());
    }

    public BatchDiagnostics setFinalPatientCount(int numFinalPatients) {
        return new BatchDiagnostics(batchExclusions, batchDetails.setFinalPatientCount(numFinalPatients));
    }

    public BatchDiagnostics setNumCohortPatients(int numCohortPatients) {
        return new BatchDiagnostics(batchExclusions, batchDetails.setNumCohortPatients(numCohortPatients));
    }

    public BatchDiagnostics setBatchDetails(BatchDetails newBatchDetails) {
        return new BatchDiagnostics(batchExclusions, newBatchDetails);
    }
}
