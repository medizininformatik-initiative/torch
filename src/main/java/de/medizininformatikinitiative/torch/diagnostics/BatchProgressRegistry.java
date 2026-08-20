package de.medizininformatikinitiative.torch.diagnostics;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which {@link PipelineStage} each currently in-progress batch is executing, so that
 * mid-run progress can be reported (e.g. via the Task API) without waiting for a batch to finish.
 *
 * <p>An entry is written each time a batch enters a stage and removed once its processing
 * attempt terminates, so a retried batch never reports a stage left over from a previous
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
