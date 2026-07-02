package de.medizininformatikinitiative.torch.diagnostics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExclusionAccTest {

    @Nested
    class RegularWriter {

        @Test
        void emptySnapshot_whenNoRecordsAdded() {
            assertThat(new ExclusionAcc().snapshot()).isEmpty();
        }

        @Test
        void snapshot_containsRecordedEntry() {
            var writer = new ExclusionAcc();
            var r = new ExclusionRecord("p1", ExclusionKind.CONSENT, null, null, null);
            writer.record(r);
            assertThat(writer.snapshot()).containsExactly(r);
        }

        @Test
        void snapshot_isImmutable_priorSnapshotUnaffectedBySubsequentRecord() {
            var writer = new ExclusionAcc();
            writer.record(new ExclusionRecord("p1", ExclusionKind.CONSENT, null, null, null));
            var snap1 = writer.snapshot();
            writer.record(new ExclusionRecord("p2", ExclusionKind.CONSENT, null, null, null));
            assertThat(snap1).hasSize(1);
        }
    }

    @Nested
    class NoopWriter {

        @Test
        void record_doesNothing() {
            var noop = ExclusionAcc.noop();
            noop.record(new ExclusionRecord("p1", ExclusionKind.CONSENT, null, null, null));
            assertThat(noop.snapshot()).isEmpty();
        }

        @Test
        void snapshot_alwaysEmpty() {
            assertThat(ExclusionAcc.noop().snapshot()).isEmpty();
        }
    }
}
