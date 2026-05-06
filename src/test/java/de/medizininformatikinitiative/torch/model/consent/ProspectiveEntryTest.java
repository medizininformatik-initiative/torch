package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.model.management.TermCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProspectiveEntryTest {

    static final TermCode CODE = new TermCode("sys", "code");
    static final TermCode RETRO = new TermCode("sys", "retro");
    static final LocalDate LOOKBACK = LocalDate.of(1900, 1, 1);

    @Test
    void constructorNullRequiredDefaultsToEmpty() {
        var entry = new ProspectiveEntry(CODE, false, null, List.of(), null);

        assertThat(entry.required()).isEmpty();
    }

    @Test
    void constructorNullRetroModifiersDefaultsToEmpty() {
        var entry = new ProspectiveEntry(CODE, false, List.of(), null, null);

        assertThat(entry.retroModifiers()).isEmpty();
    }

    @Test
    void fromJson_nullValidityGateTreatedAsFalse() {
        var entry = ProspectiveEntry.fromJson("sys", "code", null, null, null, null);

        assertThat(entry.validityGate()).isFalse();
        assertThat(entry.required()).isEmpty();
        assertThat(entry.retroModifiers()).isEmpty();
    }

    @Test
    void fromJson_nullRequiredDefaultsToEmpty() {
        var entry = ProspectiveEntry.fromJson("sys", "code", true, null, null, null);

        assertThat(entry.required()).isEmpty();
    }

    @Test
    void fromJson_withRequiredBuildsTermCodes() {
        var entry = ProspectiveEntry.fromJson("sys", "code", false, List.of("other"), null, null);

        assertThat(entry.required()).containsExactly(new TermCode("sys", "other"));
    }

    @Test
    void hasRetroModifiers_falseWhenEmpty() {
        var entry = new ProspectiveEntry(CODE, false, List.of(), List.of(), null);

        assertThat(entry.hasRetroModifiers()).isFalse();
    }

    @Test
    void hasRetroModifiers_trueWhenPresent() {
        var entry = new ProspectiveEntry(CODE, false, List.of(), List.of(new RetroModifier(RETRO)), LOOKBACK);

        assertThat(entry.hasRetroModifiers()).isTrue();
    }
}
