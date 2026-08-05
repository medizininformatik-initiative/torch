package de.medizininformatikinitiative.torch.diagnostics;

import de.medizininformatikinitiative.torch.diagnostics.exclusions.BatchExclusions;
import static java.util.Objects.requireNonNull;

/**
 * Holds various diagnostics that are recorded during the processing of a single batch.
 *
 * @param batchExclusions   the exclusion events happening during processing
 * @param batchDetails      other measurements recorded during processing
 * @param consentAudit      the Consent/Encounter resources used to calculate consent, for traceability
 */
public record BatchDiagnostics(BatchExclusions batchExclusions, BatchDetails batchDetails, ConsentAudit consentAudit) {

    public BatchDiagnostics {
        requireNonNull(batchDetails);
        requireNonNull(batchExclusions);
        requireNonNull(consentAudit);
    }

    public static BatchDiagnostics empty() {
        return new BatchDiagnostics(BatchExclusions.empty(), BatchDetails.empty(), ConsentAudit.empty());
    }

    public BatchDiagnostics setFinalPatientCount(int numFinalPatients) {
        return new BatchDiagnostics(batchExclusions, batchDetails.setFinalPatientCount(numFinalPatients), consentAudit);
    }

    public BatchDiagnostics setNumCohortPatients(int numCohortPatients) {
        return new BatchDiagnostics(batchExclusions, batchDetails.setNumCohortPatients(numCohortPatients), consentAudit);
    }

    public BatchDiagnostics setBatchDetails(BatchDetails newBatchDetails) {
        return new BatchDiagnostics(batchExclusions, newBatchDetails, consentAudit);
    }
}
