package de.medizininformatikinitiative.torch.jobhandling.failure;


import java.util.List;

/**
 * Structured issue reported during job or work-unit execution.
 *
 * <p>An {@code Issue} represents warnings or errors without embedding stack traces.</p>
 */
public record Issue(Severity severity, String msg, String diagnostics) {

    public Issue {
        if (diagnostics == null) diagnostics = "";
        if (msg == null) msg = "";
    }

    public static Issue simple(Severity severity, String msg) {
        return new Issue(severity, msg, "");
    }

    /**
     * Creates an Issue that only stores exception class + message, no stacktrace.
     */
    public static Issue fromException(Severity severity, String msg, Throwable e) {
        return new Issue(severity, msg, exceptionSummary(e));
    }

    private static String exceptionSummary(Throwable e) {
        if (e == null) return "";

        String cls = e.getClass().getName();
        String m = e.getMessage();

        if (m == null || m.isBlank()) {
            return cls;
        }
        return cls + ": " + m;
    }

    public static List<Issue> merge(List<Issue> a, List<Issue> b) {
        var out = new java.util.ArrayList<>(a);
        out.addAll(b);
        return java.util.List.copyOf(out);
    }

}
