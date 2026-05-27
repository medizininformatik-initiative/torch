package de.medizininformatikinitiative.torch.model.mapping;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentKeyTest {

    @Test
    void toStringReturnsHyphenatedRepresentation() {
        assertThat(ConsentKey.YES_YES_YES_YES.toString()).isEqualTo("yes-yes-yes-yes");
        assertThat(ConsentKey.NO_NO_NO_NO.toString()).isEqualTo("no-no-no-no");
        assertThat(ConsentKey.YES_NO_YES_NO.toString()).isEqualTo("yes-no-yes-no");
        assertThat(ConsentKey.NO_YES_NO_YES.toString()).isEqualTo("no-yes-no-yes");
        assertThat(ConsentKey.YES_YES_NO_NO.toString()).isEqualTo("yes-yes-no-no");
    }

    @Test
    void allValuesHaveNonNullNonEmptyString() {
        for (ConsentKey key : ConsentKey.values()) {
            assertThat(key.toString()).isNotNull().isNotEmpty();
        }
    }

    @Test
    void allSixteenValuesExist() {
        assertThat(ConsentKey.values()).hasSize(16);
    }
}
