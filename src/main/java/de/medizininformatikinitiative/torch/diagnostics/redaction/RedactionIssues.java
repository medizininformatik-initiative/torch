package de.medizininformatikinitiative.torch.diagnostics.redaction;

import de.medizininformatikinitiative.torch.jobhandling.failure.Issue;
import org.hl7.fhir.r4.model.DomainResource;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static de.medizininformatikinitiative.torch.diagnostics.redaction.RedactionViolation.MAX_EXAMPLES;

/**
 * Collects the required elements that redaction left absent or invalid, aggregated by resource type, element id,
 * element type, reason and cause.
 * <p>
 * Thread-safe: one instance is shared by all resources of a batch or core bundle, which are redacted in parallel.
 * Examples are relative resource ids, capped at {@link RedactionViolation#MAX_EXAMPLES} per key; Patient
 * resources are counted but never listed as examples, so no patient identifier ends up in a job issue.
 */
public class RedactionIssues {

    private record Key(String resourceType, String elementId, String elementType, RedactionViolationReason reason,
                       RedactionCause cause) {
    }

    private static final class Occurrences {
        private final AtomicLong count = new AtomicLong();
        private final Set<String> examples = new LinkedHashSet<>();

        private void add(long n, List<String> newExamples) {
            count.addAndGet(n);
            synchronized (examples) {
                for (String example : newExamples) {
                    if (examples.size() >= MAX_EXAMPLES) {
                        return;
                    }
                    examples.add(example);
                }
            }
        }

        private List<String> examples() {
            synchronized (examples) {
                return List.copyOf(examples);
            }
        }
    }

    private final Map<Key, Occurrences> occurrences = new ConcurrentHashMap<>();

    /**
     * Records one occurrence of {@code elementId} in {@code resource} left absent or invalid for {@code reason}.
     */
    public void record(DomainResource resource, String elementId, String elementType, RedactionViolationReason reason) {
        record(resource, elementId, elementType, reason, null);
    }

    /**
     * Records like {@link #record(DomainResource, String, String, RedactionViolationReason)}, naming the innermost child
     * whose failure caused it; occurrences with different causes are kept apart.
     *
     * @param cause the innermost child that failed, or {@code null} if the element failed itself
     */
    public void record(DomainResource resource, String elementId, String elementType, RedactionViolationReason reason,
                       RedactionCause cause) {
        List<String> example = resource.hasIdElement() && !"Patient".equals(resource.fhirType())
                ? List.of(resource.fhirType() + "/" + resource.getIdElement().getIdPart())
                : List.of();
        occurrences.computeIfAbsent(new Key(resource.fhirType(), elementId, elementType, reason, cause), key -> new Occurrences())
                .add(1, example);
    }

    /**
     * Adds all occurrences recorded in {@code other} to this collector.
     */
    public void addAll(RedactionIssues other) {
        other.violations().forEach(violation -> occurrences
                .computeIfAbsent(new Key(violation.resourceType(), violation.elementId(), violation.elementType(), violation.reason(),
                                violation.cause()),
                        key -> new Occurrences())
                .add(violation.count(), violation.examples()));
    }

    /**
     * @return one violation per key, ordered by resource type, element id, element type, reason and cause
     */
    public List<RedactionViolation> violations() {
        return occurrences.entrySet().stream()
                .map(entry -> new RedactionViolation(entry.getKey().resourceType(), entry.getKey().elementId(),
                        entry.getKey().elementType(), entry.getKey().reason(), entry.getKey().cause(), entry.getValue().count.get(),
                        entry.getValue().examples()))
                .sorted(Comparator.comparing(RedactionViolation::resourceType)
                        .thenComparing(RedactionViolation::elementId)
                        .thenComparing(RedactionViolation::elementType)
                        .thenComparing(RedactionViolation::reason)
                        .thenComparing(violation -> violation.cause() == null ? "" : violation.cause().elementId()))
                .toList();
    }

    /**
     * @return one {@link de.medizininformatikinitiative.torch.jobhandling.failure.Severity#WARNING} issue per key
     */
    public List<Issue> toIssues() {
        return violations().stream().map(Issue::of).toList();
    }
}
