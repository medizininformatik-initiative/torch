package de.medizininformatikinitiative.torch.jobhandling.failure;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.diagnostics.redaction.RedactionViolation;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured issue reported during job or work-unit execution.
 *
 * <p>An {@code Issue} represents warnings or errors without embedding stack traces.</p>
 *
 * @param redactionViolation set only for issues created by {@link #of(RedactionViolation)}, so that issues about the
 *                           same element can be merged across batches; {@code null} otherwise and then omitted
 *                           from JSON
 */
public record Issue(@JsonProperty Severity severity, @JsonProperty String msg, @JsonProperty String diagnostics,
                    @JsonProperty @JsonInclude(JsonInclude.Include.NON_NULL) RedactionViolation redactionViolation) {

    public Issue {
        if (diagnostics == null) diagnostics = "";
        if (msg == null) msg = "";
    }

    public Issue(Severity severity, String msg, String diagnostics) {
        this(severity, msg, diagnostics, null);
    }

    /**
     * Creates a {@link Severity#WARNING} issue whose message and diagnostics describe {@code violation}.
     */
    public static Issue of(RedactionViolation violation) {
        return new Issue(Severity.WARNING, violation.message(), violation.diagnostics(), violation);
    }

    public static Issue simple(Severity severity, String msg) {
        return new Issue(severity, msg, "");
    }

    /**
     * Creates an Issue that only stores exception class + message and, if different, the root cause's class +
     * message, no stacktrace.
     */
    public static Issue fromException(Severity severity, String msg, Throwable e) {
        return new Issue(severity, msg, exceptionSummary(e));
    }

    private static String exceptionSummary(Throwable e) {
        if (e == null) return "";

        String cls = e.getClass().getName();
        String m = e.getMessage();

        String summary = (m == null || m.isBlank()) ? cls : cls + ": " + m;

        if (RetryabilityUtil.rootCause(e) == e) {
            return summary;
        }
        return summary + " (root cause: " + RetryabilityUtil.rootCauseMessage(e) + ")";
    }

    /**
     * Appends {@code newIssues} to {@code issues}. A new redaction-violation issue whose key matches an existing one
     * is merged into it (counts summed, examples combined) instead of being appended.
     *
     * @return a new list; the inputs are not modified
     */
    public static List<Issue> addMergingRedactionViolations(List<Issue> issues, List<Issue> newIssues) {
        List<Issue> out = new ArrayList<>(issues);
        for (Issue issue : newIssues) {
            int index = issue.redactionViolation == null ? -1 : indexOfSameViolation(out, issue.redactionViolation);
            if (index < 0) {
                out.add(issue);
            } else {
                out.set(index, of(out.get(index).redactionViolation.merge(issue.redactionViolation)));
            }
        }
        return out;
    }

    private static int indexOfSameViolation(List<Issue> issues, RedactionViolation violation) {
        for (int i = 0; i < issues.size(); i++) {
            RedactionViolation existing = issues.get(i).redactionViolation;
            if (existing != null && existing.sameKey(violation)) {
                return i;
            }
        }
        return -1;
    }

    public static List<Issue> merge(List<Issue> a, List<Issue> b) {
        var out = new java.util.ArrayList<>(a);
        out.addAll(b);
        return java.util.List.copyOf(out);
    }

}
