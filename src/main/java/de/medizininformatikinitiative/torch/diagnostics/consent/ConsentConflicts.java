package de.medizininformatikinitiative.torch.diagnostics.consent;

import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.util.Objects.requireNonNull;

/**
 * Collects the {@link ConsentConflictEvent}s found while calculating a batch's consent.
 * <p>
 * Always recorded, unlike the opt-in {@link ConsentDiagnostics}, because a conflict means the source data is invalid.
 * <p>
 * Not a record to avoid mutations of the queue without the dedicated methods.
 */
public class ConsentConflicts {

    private final Queue<ConsentConflictEvent> events;

    public ConsentConflicts(Queue<ConsentConflictEvent> events) {
        this.events = requireNonNull(events);
    }

    public static ConsentConflicts empty() {
        return new ConsentConflicts(new ConcurrentLinkedQueue<>());
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ConsentConflicts other)) {
            return false;
        }
        return this.events().equals(other.events());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(events());
    }

    public void add(ConsentConflictEvent event) {
        events.add(event);
    }

    public List<ConsentConflictEvent> events() {
        return events.stream().toList();
    }
}
