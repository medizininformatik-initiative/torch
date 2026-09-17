package de.medizininformatikinitiative.torch.diagnostics;

import de.medizininformatikinitiative.torch.diagnostics.consent.ConsentDiagnostics;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.BatchExclusions;
import static java.util.Objects.requireNonNull;

/**
 * Holds various diagnostics that are recorded during the processing of a single batch.
 *
 * @param batchExclusions     the exclusion events happening during processing
 * @param batchDetails        other measurements recorded during processing
 * @param consentAudit        the Consent/Encounter resources used to calculate consent, for traceability
 * @param consentDiagnostics  opt-in raw provisions/final periods/per-resource consent decisions (see
 *                            {@code consentDiagnostics} on {@code POST /fhir/$extract-data})
 */
public record BatchDiagnostics(BatchExclusions batchExclusions, BatchDetails batchDetails, ConsentAudit consentAudit,
                               ConsentDiagnostics consentDiagnostics) {

    public BatchDiagnostics {
        requireNonNull(batchDetails);
        requireNonNull(batchExclusions);
        requireNonNull(consentAudit);
        requireNonNull(consentDiagnostics);
    }

    public static BatchDiagnostics empty() {
        return new BatchDiagnostics(BatchExclusions.empty(), BatchDetails.empty(), ConsentAudit.empty(), ConsentDiagnostics.disabled());
    }

    public BatchDiagnostics setFinalPatientCount(int numFinalPatients) {
        return new BatchDiagnostics(batchExclusions, batchDetails.setFinalPatientCount(numFinalPatients), consentAudit, consentDiagnostics);
    }

    public BatchDiagnostics setNumCohortPatients(int numCohortPatients) {
        return new BatchDiagnostics(batchExclusions, batchDetails.setNumCohortPatients(numCohortPatients), consentAudit, consentDiagnostics);
    }

    public BatchDiagnostics setBatchDetails(BatchDetails newBatchDetails) {
        return new BatchDiagnostics(batchExclusions, newBatchDetails, consentAudit, consentDiagnostics);
    }

    public BatchDiagnostics withConsentDiagnosticsEnabled(boolean enabled) {
        return new BatchDiagnostics(batchExclusions, batchDetails, consentAudit, ConsentDiagnostics.create(enabled));
    }
}
