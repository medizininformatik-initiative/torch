package de.medizininformatikinitiative.torch.management;

import org.hl7.fhir.r4.model.OperationOutcome;

public class OperationOutcomeCreator {

    public static OperationOutcome createOperationOutcome(String jobId, Throwable throwable) {
        OperationOutcome operationOutcome = new OperationOutcome();
        operationOutcome.setId("job-" + jobId + "-error");

        OperationOutcome.OperationOutcomeIssueComponent issueComponent = new OperationOutcome.OperationOutcomeIssueComponent();
        issueComponent.setSeverity(OperationOutcome.IssueSeverity.ERROR);
        issueComponent.setCode(createIssueType(throwable));
        issueComponent.setDiagnostics(throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
        operationOutcome.addIssue(issueComponent);
        return operationOutcome;
    }

    static OperationOutcome.IssueType createIssueType(Throwable throwable) {
        return switch (throwable.getClass().getSimpleName()) {
            case "ValidationException", "IllegalArgumentException" -> OperationOutcome.IssueType.INVALID;
            case "SecurityException" -> OperationOutcome.IssueType.SECURITY;
            default -> OperationOutcome.IssueType.EXCEPTION;
        };
    }


}
