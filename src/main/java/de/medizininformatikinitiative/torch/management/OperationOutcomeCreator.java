package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.jobhandling.Issue;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.Severity;
import org.hl7.fhir.r4.model.OperationOutcome;

public class OperationOutcomeCreator {

    public static OperationOutcome createOperationOutcome(String jobId, Throwable throwable) {
        OperationOutcome operationOutcome = new OperationOutcome();
        operationOutcome.setId("job-" + jobId + "-error");

        OperationOutcome.OperationOutcomeIssueComponent issueComponent = new OperationOutcome.OperationOutcomeIssueComponent();
        issueComponent.setSeverity(OperationOutcome.IssueSeverity.FATAL);
        issueComponent.setCode(createIssueType(throwable));
        issueComponent.setDiagnostics(throwable.getMessage());
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

    public static OperationOutcome fromJob(Job job) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.setId("job-" + job.id() + "-outcome");

        for (Issue issue : job.issues()) {
            OperationOutcome.OperationOutcomeIssueComponent comp =
                    new OperationOutcome.OperationOutcomeIssueComponent();

            comp.setSeverity(mapSeverity(issue.severity()));

            // If an exception is present → use existing mapping
            if (issue.exception().isPresent()) {
                comp.setCode(mapIssueType(issue.severity()));
                if (issue.exception().isPresent()) {
                    comp.setCode(createIssueType(issue.exception().get()));
                }
                comp.setDiagnostics(issue.msg() != null ? issue.msg()
                        : issue.exception().get().getMessage());
            } else {
                // No exception → informational issue with a generic code
                comp.setCode(OperationOutcome.IssueType.INFORMATIONAL);
                comp.setDiagnostics(issue.msg());
            }

            outcome.addIssue(comp);
        }

        return outcome;
    }

    private static OperationOutcome.IssueType mapIssueType(Severity severity) {
        return switch (severity) {
            case SUCCESS, INFO -> OperationOutcome.IssueType.INFORMATIONAL;
            case WARNING -> OperationOutcome.IssueType.PROCESSING;
            case ERROR, FATAL -> OperationOutcome.IssueType.EXCEPTION;
        };
    }


    private static OperationOutcome.IssueSeverity mapSeverity(Severity severity) {
        return switch (severity) {
            case FATAL -> OperationOutcome.IssueSeverity.FATAL;
            case ERROR -> OperationOutcome.IssueSeverity.ERROR;
            case WARNING -> OperationOutcome.IssueSeverity.WARNING;
            case INFO,
                 SUCCESS -> OperationOutcome.IssueSeverity.INFORMATION;
        };
    }


}
