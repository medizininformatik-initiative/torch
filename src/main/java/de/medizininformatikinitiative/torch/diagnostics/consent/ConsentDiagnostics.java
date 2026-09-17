package de.medizininformatikinitiative.torch.diagnostics.consent;

import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.util.Objects.requireNonNull;

/**
 * Collects the opt-in consent diagnostics (raw provisions, final consent periods and per-resource consent
 * decisions) recorded during processing of a single batch.
 * <p>
 * Recording is a no-op when disabled, so that enabling this per job (via {@code consentDiagnostics} on
 * {@code POST /fhir/$extract-data}) is the only way to pay for its volume — a production run without it
 * incurs no extra allocation: {@link #disabled()} shares one set of empty backing collections across every
 * disabled instance rather than allocating fresh ones per batch.
 * <p>
 * Not a record to avoid mutations of the collections without the dedicated methods.
 */
public class ConsentDiagnostics {

    private static final Queue<RawProvisionEvent> DISABLED_RAW_PROVISIONS = new ConcurrentLinkedQueue<>();
    private static final Queue<FinalPeriodEvent> DISABLED_FINAL_PERIODS = new ConcurrentLinkedQueue<>();
    private static final Queue<ConsentConsideredResourceEvent> DISABLED_CONSIDERED_RESOURCES = new ConcurrentLinkedQueue<>();

    private final boolean enabled;
    private final Queue<RawProvisionEvent> rawProvisions;
    private final Queue<FinalPeriodEvent> finalPeriods;
    private final Queue<ConsentConsideredResourceEvent> consideredResources;

    private ConsentDiagnostics(boolean enabled, Queue<RawProvisionEvent> rawProvisions,
                               Queue<FinalPeriodEvent> finalPeriods,
                               Queue<ConsentConsideredResourceEvent> consideredResources) {
        this.enabled = enabled;
        this.rawProvisions = requireNonNull(rawProvisions);
        this.finalPeriods = requireNonNull(finalPeriods);
        this.consideredResources = requireNonNull(consideredResources);
    }

    public static ConsentDiagnostics create(boolean enabled) {
        if (!enabled) {
            return disabled();
        }
        return new ConsentDiagnostics(true, new ConcurrentLinkedQueue<>(), new ConcurrentLinkedQueue<>(),
                new ConcurrentLinkedQueue<>());
    }

    public static ConsentDiagnostics disabled() {
        return new ConsentDiagnostics(false, DISABLED_RAW_PROVISIONS, DISABLED_FINAL_PERIODS,
                DISABLED_CONSIDERED_RESOURCES);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEmpty() {
        return rawProvisions.isEmpty() && finalPeriods.isEmpty() && consideredResources.isEmpty();
    }

    /**
     * Compares by recorded content only; {@code enabled} governs whether future {@code addX} calls are no-ops and is not itself part of the diagnostics.
     */
    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ConsentDiagnostics other)) {
            return false;
        }
        return this.getRawProvisions().equals(other.getRawProvisions()) &&
                this.getFinalPeriods().equals(other.getFinalPeriods()) &&
                this.getConsideredResources().equals(other.getConsideredResources());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getRawProvisions(), getFinalPeriods(), getConsideredResources());
    }

    public void addRawProvision(RawProvisionEvent event) {
        if (enabled) rawProvisions.add(event);
    }

    public void addFinalPeriod(FinalPeriodEvent event) {
        if (enabled) finalPeriods.add(event);
    }

    public void addConsideredResource(ConsentConsideredResourceEvent event) {
        if (enabled) consideredResources.add(event);
    }

    public List<RawProvisionEvent> getRawProvisions() {
        return rawProvisions.stream().toList();
    }

    public List<FinalPeriodEvent> getFinalPeriods() {
        return finalPeriods.stream().toList();
    }

    public List<ConsentConsideredResourceEvent> getConsideredResources() {
        return consideredResources.stream().toList();
    }
}
