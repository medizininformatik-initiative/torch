package de.medizininformatikinitiative.torch.jobhandling.failure;

import org.junit.jupiter.api.Test;
import reactor.core.Exceptions;

import static org.assertj.core.api.Assertions.assertThat;

class IssueTest {

    @Test
    void fromExceptionIncludesRootCause() {
        var rootCause = new IllegalStateException("404 Not Found from GET http://blaze/fhir/Observation/__page/abc");
        var exhausted = Exceptions.retryExhausted("Retries exhausted: 20/20", rootCause);

        var issue = Issue.fromException(Severity.ERROR, "Batch failed", exhausted);

        assertThat(issue.diagnostics())
                .contains("Retries exhausted: 20/20")
                .contains("IllegalStateException: 404 Not Found from GET http://blaze/fhir/Observation/__page/abc");
    }

    @Test
    void fromExceptionWithoutCauseHasNoRootCauseSuffix() {
        var issue = Issue.fromException(Severity.ERROR, "Batch failed", new IllegalStateException("boom"));

        assertThat(issue.diagnostics()).isEqualTo("java.lang.IllegalStateException: boom");
    }

    @Test
    void fromExceptionWithoutMessageUsesClassName() {
        var issue = Issue.fromException(Severity.ERROR, "Batch failed", new IllegalStateException());

        assertThat(issue.diagnostics()).isEqualTo("java.lang.IllegalStateException");
    }

    @Test
    void fromExceptionWithBlankMessageUsesClassName() {
        var issue = Issue.fromException(Severity.ERROR, "Batch failed", new IllegalStateException(" "));

        assertThat(issue.diagnostics()).isEqualTo("java.lang.IllegalStateException");
    }
}
