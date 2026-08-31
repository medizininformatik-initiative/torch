package de.medizininformatikinitiative.torch.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BatchProgressRegistryTest {

    private final BatchProgressRegistry registry = new BatchProgressRegistry();

    @Test
    void currentStage_isEmpty_forUnknownBatch() {
        assertThat(registry.currentStage(UUID.randomUUID())).isEmpty();
    }

    @Test
    void currentStage_returnsLastEnteredStage() {
        UUID batchId = UUID.randomUUID();

        registry.enter(batchId, PipelineStage.DIRECT_LOAD);
        assertThat(registry.currentStage(batchId)).contains(PipelineStage.DIRECT_LOAD);

        registry.enter(batchId, PipelineStage.COPY_REDACT);
        assertThat(registry.currentStage(batchId)).contains(PipelineStage.COPY_REDACT);
    }

    @Test
    void clear_removesTheEntry() {
        UUID batchId = UUID.randomUUID();
        registry.enter(batchId, PipelineStage.DIRECT_LOAD);

        registry.clear(batchId);

        assertThat(registry.currentStage(batchId)).isEmpty();
    }

    @Test
    void entriesForDifferentBatches_areIndependent() {
        UUID batch1 = UUID.randomUUID();
        UUID batch2 = UUID.randomUUID();

        registry.enter(batch1, PipelineStage.DIRECT_LOAD);
        registry.enter(batch2, PipelineStage.COPY_REDACT);
        registry.clear(batch1);

        assertThat(registry.currentStage(batch1)).isEmpty();
        assertThat(registry.currentStage(batch2)).contains(PipelineStage.COPY_REDACT);
    }
}
