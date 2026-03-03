package de.medizininformatikinitiative.torch.accumulators;

import de.medizininformatikinitiative.torch.jobhandling.failure.Issue;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public record Acc<T>(
        T value,
        List<Issue> issues
) {
    public static <T> Acc<T> ok(T value) {
        return new Acc<>(value, List.of());
    }

    public Acc<T> addInfo(String msg, String detail) {
        return addIssue(new Issue(Severity.INFO, msg, detail));
    }

    public Acc<T> addWarning(String msg, String detail) {
        return addIssue(new Issue(Severity.WARNING, msg, detail));
    }

    public Acc<T> addIssue(Issue issue) {
        List<Issue> merged = new ArrayList<>(issues);
        merged.add(issue);
        return new Acc<>(value, List.copyOf(merged));
    }

    public <R> Acc<R> map(Function<T, R> f) {
        return new Acc<>(f.apply(value), issues);
    }

    public Acc<T> mergeIssuesFrom(Acc<?> other) {
        List<Issue> merged = new ArrayList<>(other.issues());
        merged.addAll(issues);
        return new Acc<>(value, List.copyOf(merged));
    }
}
