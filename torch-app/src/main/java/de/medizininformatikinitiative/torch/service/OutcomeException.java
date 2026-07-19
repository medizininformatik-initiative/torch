package de.medizininformatikinitiative.torch.service;

import org.hl7.fhir.r4.model.OperationOutcome;

import static java.util.Objects.requireNonNull;

class OutcomeException extends Exception {

    private final OperationOutcome outcome;

    OutcomeException(OperationOutcome outcome) {
        this.outcome = requireNonNull(outcome);
    }

    @Override
    public String getMessage() {
        var issue = outcome.getIssueFirstRep();
        return issue.getDiagnostics() == null ? "unknown error" : issue.getDiagnostics();
    }
}
