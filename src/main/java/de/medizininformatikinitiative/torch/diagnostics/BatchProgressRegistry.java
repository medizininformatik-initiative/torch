package de.medizininformatikinitiative.torch.diagnostics;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which {@link PipelineStage} each currently in-progress batch or core is executing, so
 * that mid-run progress can be reported (e.g. via the Task API) without waiting for it to finish.
 * Batches are keyed by batch id, the core by its job id.
 *
 * <p>An entry is written each time a work unit enters a stage and removed once its processing
 * attempt terminates, so a retried work unit never reports a stage left over from a previous
 * attempt.
 */
@Component
public class BatchProgressRegistry {

    private final Map<UUID, PipelineStage> currentStage = new ConcurrentHashMap<>();

    public void enter(UUID batchId, PipelineStage stage) {
        currentStage.put(batchId, stage);
    }

    public Optional<PipelineStage> currentStage(UUID batchId) {
        return Optional.ofNullable(currentStage.get(batchId));
    }

    public void clear(UUID batchId) {
        currentStage.remove(batchId);
    }
}
