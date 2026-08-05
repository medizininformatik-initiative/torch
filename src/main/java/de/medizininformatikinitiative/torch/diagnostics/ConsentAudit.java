package de.medizininformatikinitiative.torch.diagnostics;

import org.hl7.fhir.r4.model.Resource;

import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.util.Objects.requireNonNull;

/**
 * Collects the minimized Consent/Encounter resources used to calculate a batch's patient
 * consent time windows, for later independent audit and recalculation.
 * <p>
 * Populated during consent fetching regardless of whether the batch ultimately succeeds or
 * is skipped for lack of consent, so entries survive a
 * {@link de.medizininformatikinitiative.torch.exceptions.ConsentViolatedException}.
 * <p>
 * Not a record to avoid mutations of the queue without the dedicated methods.
 */
public class ConsentAudit {

    private final Queue<ConsentAuditEntry> entries;

    public ConsentAudit(Queue<ConsentAuditEntry> entries) {
        this.entries = requireNonNull(entries);
    }

    public static ConsentAudit empty() {
        return new ConsentAudit(new ConcurrentLinkedQueue<>());
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ConsentAudit other)) {
            return false;
        }
        return this.entries().equals(other.entries());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(entries());
    }

    public void add(String patientId, Resource resource) {
        entries.add(new ConsentAuditEntry(patientId, resource));
    }

    /**
     * Creates a copy of the currently collected entries.
     *
     * @return a list of the consent audit entries.
     */
    public List<ConsentAuditEntry> entries() {
        return entries.stream().toList();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
