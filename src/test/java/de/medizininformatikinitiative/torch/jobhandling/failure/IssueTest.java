package de.medizininformatikinitiative.torch.jobhandling.failure;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.medizininformatikinitiative.torch.diagnostics.redaction.RedactionCause;
import de.medizininformatikinitiative.torch.diagnostics.redaction.RedactionViolation;
import org.junit.jupiter.api.Test;
import reactor.core.Exceptions;

import java.util.List;

import static de.medizininformatikinitiative.torch.diagnostics.redaction.RedactionViolationReason.NO_SETTER;
import static de.medizininformatikinitiative.torch.diagnostics.redaction.RedactionViolationReason.UNMASKABLE_REQUIRED_CHILD;
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

    private static Issue violation(String elementId, long count, String... examples) {
        return Issue.of(new RedactionViolation("Observation", elementId, "SampledData", UNMASKABLE_REQUIRED_CHILD, count, List.of(examples)));
    }

    @Test
    void addMergingRedactionViolationsMergesSameKey() {
        Issue plain = Issue.simple(Severity.WARNING, "Batch skipped");
        List<Issue> existing = List.of(plain, violation("Observation.component", 2, "Observation/a", "Observation/b"));

        List<Issue> merged = Issue.addMergingRedactionViolations(existing,
                List.of(violation("Observation.component", 3, "Observation/b", "Observation/c", "Observation/d")));

        assertThat(merged).containsExactly(plain, violation("Observation.component", 5, "Observation/a", "Observation/b", "Observation/c"));
    }

    @Test
    void addMergingRedactionViolationsAppendsOtherKeysAndPlainIssues() {
        Issue first = violation("Observation.component", 1);
        Issue otherElement = violation("Observation.code", 1);
        Issue otherReason = Issue.of(new RedactionViolation("Observation", "Observation.component", "SampledData", NO_SETTER, 1, List.of()));
        Issue plain = Issue.simple(Severity.WARNING, "Batch skipped");

        List<Issue> merged = Issue.addMergingRedactionViolations(List.of(first), List.of(otherElement, otherReason, plain, plain));

        assertThat(merged).containsExactly(first, otherElement, otherReason, plain, plain);
    }

    @Test
    void plainIssueSerializesWithoutRedactionViolation() throws Exception {
        String json = new ObjectMapper().writeValueAsString(Issue.simple(Severity.WARNING, "Batch skipped"));

        assertThat(json).isEqualTo("{\"severity\":\"WARNING\",\"msg\":\"Batch skipped\",\"diagnostics\":\"\"}");
    }

    @Test
    void issueWithoutRedactionViolationFieldDeserializes() throws Exception {
        Issue issue = new ObjectMapper().readValue("{\"severity\":\"WARNING\",\"msg\":\"m\",\"diagnostics\":\"d\"}", Issue.class);

        assertThat(issue).isEqualTo(new Issue(Severity.WARNING, "m", "d"));
    }

    @Test
    void redactionViolationIssueRoundTrips() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Issue issue = violation("Observation.component", 7, "Observation/a");

        String json = mapper.writeValueAsString(issue);

        assertThat(json).doesNotContain("cause");
        assertThat(mapper.readValue(json, Issue.class)).isEqualTo(issue);
    }

    @Test
    void redactionViolationIssueWithCauseRoundTrips() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Issue issue = Issue.of(new RedactionViolation("Observation", "Observation.component", "BackboneElement", UNMASKABLE_REQUIRED_CHILD,
                new RedactionCause("Observation.component.code", "NoSuchType", NO_SETTER), 1, List.of()));

        assertThat(mapper.readValue(mapper.writeValueAsString(issue), Issue.class)).isEqualTo(issue);
    }
}
