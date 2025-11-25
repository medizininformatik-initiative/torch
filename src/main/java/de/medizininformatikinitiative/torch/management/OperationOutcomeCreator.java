package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.failure.Issue;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.jspecify.annotations.NonNull;

public class OperationOutcomeCreator {

    public static OperationOutcome simple(Severity severity, OperationOutcome.IssueType code, String diagnostics) {
        OperationOutcome outcome = new OperationOutcome();
        outcome.setId("request-outcome");

        InstantType timestamp = InstantType.now();
        Meta meta = new Meta();
        meta.setLastUpdatedElement(timestamp);
        outcome.setMeta(meta);

        OperationOutcome.OperationOutcomeIssueComponent issue = new OperationOutcome.OperationOutcomeIssueComponent();
        issue.setSeverity(mapSeverity(severity));
        issue.setCode(code);
        issue.setDiagnostics(diagnostics);
        outcome.addIssue(issue);

        return outcome;
    }

    public static OperationOutcome createOperationOutcome(Throwable throwable) {
        OperationOutcome operationOutcome = new OperationOutcome();
        operationOutcome.setId("job-error");

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

        InstantType timestamp = InstantType.now();
        Meta meta = new Meta();
        meta.setLastUpdatedElement(timestamp);
        outcome.setMeta(meta);
        for (Issue issue : job.issues()) {
            OperationOutcome.OperationOutcomeIssueComponent componentIssue = getOperationOutcomeIssueComponent(issue);

            outcome.addIssue(componentIssue);
        }
        OperationOutcome.OperationOutcomeIssueComponent componentInfo = new OperationOutcome.OperationOutcomeIssueComponent();
        componentInfo.setSeverity(OperationOutcome.IssueSeverity.INFORMATION);
        componentInfo.setCode(OperationOutcome.IssueType.INFORMATIONAL);
        componentInfo.setDiagnostics("Job " + job.id() + "\n status: " + job.status() + "\n batch progress: " + job.calculateBatchProgress() + "%\n cohort Size: " + job.cohortSize());
        outcome.addIssue(componentInfo);
        return outcome;
    }

    private static OperationOutcome.@NonNull OperationOutcomeIssueComponent getOperationOutcomeIssueComponent(Issue issue) {
        OperationOutcome.OperationOutcomeIssueComponent comp =
                new OperationOutcome.OperationOutcomeIssueComponent();

        // Severity → INFO | WARNING | ERROR
        comp.setSeverity(mapSeverity(issue.severity()));

        // Type → EXCEPTION, PROCESSING, INFORMATIONAL etc.
        comp.setCode(mapIssueType(issue.severity()));
        comp.setDiagnostics(issue.msg() +
                (issue.diagnostics().isBlank() ? "" : (" :: " + issue.diagnostics())));
        return comp;
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
