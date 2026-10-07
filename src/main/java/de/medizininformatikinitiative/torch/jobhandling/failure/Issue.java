package de.medizininformatikinitiative.torch.jobhandling.failure;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Structured issue reported during job or work-unit execution.
 *
 * <p>An {@code Issue} represents warnings or errors without embedding stack traces.</p>
 */
public record Issue(@JsonProperty Severity severity, @JsonProperty String msg, @JsonProperty String diagnostics) {

    public Issue {
        if (diagnostics == null) diagnostics = "";
        if (msg == null) msg = "";
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

    public static List<Issue> merge(List<Issue> a, List<Issue> b) {
        var out = new java.util.ArrayList<>(a);
        out.addAll(b);
        return java.util.List.copyOf(out);
    }

}
