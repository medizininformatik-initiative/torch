package de.medizininformatikinitiative.torch.management;

import de.medizininformatikinitiative.torch.jobhandling.BatchState;
import de.medizininformatikinitiative.torch.jobhandling.Job;
import de.medizininformatikinitiative.torch.jobhandling.JobParameters;
import de.medizininformatikinitiative.torch.jobhandling.JobPriority;
import de.medizininformatikinitiative.torch.jobhandling.JobStatus;
import de.medizininformatikinitiative.torch.jobhandling.failure.Issue;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;
import de.medizininformatikinitiative.torch.jobhandling.workunit.WorkUnitState;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationOutcomeCreatorTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Job job(UUID id, JobStatus status, int cohortSize, List<Issue> issues) {
        // Stuff we don’t test here → mocks / empty.
        WorkUnitState cohortState = mock(WorkUnitState.class);
        WorkUnitState coreState = mock(WorkUnitState.class);
        Map<UUID, BatchState> batches = Map.of();

        JobParameters params = mock(JobParameters.class);
        JobPriority priority = mock(JobPriority.class);

        Instant now = Instant.now();

        return new Job(
                id,
                status,
                cohortState,
                cohortSize,
                batches,
                now,
                now,
                Optional.empty(),
                issues,
                params,
                priority,
                coreState
        );
    }

    private static Issue issue(Severity severity, String msg, String diagnostics) {
        Issue i = mock(Issue.class);
        when(i.severity()).thenReturn(severity);
        when(i.msg()).thenReturn(msg);
        when(i.diagnostics()).thenReturn(diagnostics);
        return i;
    }

    // -------------------------------------------------------------------------
    // createIssueType(Throwable) + createOperationOutcome(Throwable)
    // -------------------------------------------------------------------------

    @Nested
    class ThrowableMappingTests {

        @Test
        void createIssueType_illegalArgument_isInvalid() {
            assertThat(OperationOutcomeCreator.createIssueType(new IllegalArgumentException("x")))
                    .isEqualTo(OperationOutcome.IssueType.INVALID);
        }

        @Test
        void createIssueType_securityException_isSecurity() {
            assertThat(OperationOutcomeCreator.createIssueType(new SecurityException("x")))
                    .isEqualTo(OperationOutcome.IssueType.SECURITY);
        }

        @Test
        void createIssueType_other_isException() {
            assertThat(OperationOutcomeCreator.createIssueType(new RuntimeException("x")))
                    .isEqualTo(OperationOutcome.IssueType.EXCEPTION);
        }

        @Test
        void createOperationOutcome_createsSingleFatalIssue() {
            OperationOutcome oo = OperationOutcomeCreator.createOperationOutcome(
                    new IllegalArgumentException("bad input")
            );

            assertThat(oo.getId()).isEqualTo("job-error");
            assertThat(oo.getIssue()).hasSize(1);

            var issue = oo.getIssueFirstRep();
            assertThat(issue.getSeverity()).isEqualTo(OperationOutcome.IssueSeverity.FATAL);
            assertThat(issue.getCode()).isEqualTo(OperationOutcome.IssueType.INVALID);
            assertThat(issue.getDiagnostics()).isEqualTo("bad input");
        }
    }

    // -------------------------------------------------------------------------
    // fromJob(Job) branch coverage (severity/type mapping + diagnostics formatting)
    // -------------------------------------------------------------------------

    @Nested
    class FromJobTests {

        @Test
        void fromJob_addsOneIssuePerJobIssue_andAddsSummaryInfoIssue() {
            UUID id = UUID.randomUUID();

            List<Issue> issues = List.of(
                    issue(Severity.SUCCESS, "ok", ""),
                    issue(Severity.INFO, "info", ""),
                    issue(Severity.WARNING, "warn", ""),
                    issue(Severity.ERROR, "error", ""),
                    issue(Severity.FATAL, "fatal", "")
            );

            Job j = job(id, JobStatus.FAILED, 123, issues);

            OperationOutcome oo = OperationOutcomeCreator.fromJob(j);

            // 5 mapped + 1 summary
            assertThat(oo.getIssue()).hasSize(6);

            // SUCCESS -> INFORMATION / INFORMATIONAL
            assertThat(oo.getIssue().get(0).getSeverity()).isEqualTo(OperationOutcome.IssueSeverity.INFORMATION);
            assertThat(oo.getIssue().get(0).getCode()).isEqualTo(OperationOutcome.IssueType.INFORMATIONAL);

            // INFO -> INFORMATION / INFORMATIONAL
            assertThat(oo.getIssue().get(1).getSeverity()).isEqualTo(OperationOutcome.IssueSeverity.INFORMATION);
            assertThat(oo.getIssue().get(1).getCode()).isEqualTo(OperationOutcome.IssueType.INFORMATIONAL);

            // WARNING -> WARNING / PROCESSING
            assertThat(oo.getIssue().get(2).getSeverity()).isEqualTo(OperationOutcome.IssueSeverity.WARNING);
            assertThat(oo.getIssue().get(2).getCode()).isEqualTo(OperationOutcome.IssueType.PROCESSING);

            // ERROR -> ERROR / EXCEPTION
            assertThat(oo.getIssue().get(3).getSeverity()).isEqualTo(OperationOutcome.IssueSeverity.ERROR);
            assertThat(oo.getIssue().get(3).getCode()).isEqualTo(OperationOutcome.IssueType.EXCEPTION);

            // FATAL -> FATAL / EXCEPTION
            assertThat(oo.getIssue().get(4).getSeverity()).isEqualTo(OperationOutcome.IssueSeverity.FATAL);
            assertThat(oo.getIssue().get(4).getCode()).isEqualTo(OperationOutcome.IssueType.EXCEPTION);

            // summary issue is always appended
            var summary = oo.getIssue().get(5);
            assertThat(summary.getSeverity()).isEqualTo(OperationOutcome.IssueSeverity.INFORMATION);
            assertThat(summary.getCode()).isEqualTo(OperationOutcome.IssueType.INFORMATIONAL);
            assertThat(summary.getDiagnostics())
                    .contains("Job " + id)
                    .contains("status: " + JobStatus.FAILED)
                    .contains("batch progress: ")
                    .contains("cohort Size: 123");
        }

        @Test
        void fromJob_appendsDiagnostics_onlyWhenNonBlank() {
            UUID id = UUID.randomUUID();

            Job withDetails = job(
                    id,
                    JobStatus.PENDING,
                    0,
                    List.of(issue(Severity.ERROR, "Something failed", "details"))
            );
            OperationOutcome oo1 = OperationOutcomeCreator.fromJob(withDetails);

            assertThat(oo1.getIssueFirstRep().getDiagnostics())
                    .isEqualTo("Something failed :: details");

            Job withoutDetails = job(
                    id,
                    JobStatus.PENDING,
                    0,
                    List.of(issue(Severity.ERROR, "Something failed", ""))
            );
            OperationOutcome oo2 = OperationOutcomeCreator.fromJob(withoutDetails);

            assertThat(oo2.getIssueFirstRep().getDiagnostics())
                    .isEqualTo("Something failed");
        }
    }
}
