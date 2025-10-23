package de.medizininformatikinitiative.torch.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigUtilsTest {

    @Test
    void testIsNotSet_withNull() {
        assertThat(ConfigUtils.isNotSet(null)).isTrue();
    }

    @Test
    void testIsNotSet_withBlankString() {
        assertThat(ConfigUtils.isNotSet("")).isTrue();
    }

    @Test
    void testIsNotSet_withLiteralQuotes() {
        assertThat(ConfigUtils.isNotSet("\"\"")).isTrue();
    }

    @Test
    void testIsNotSet_withCharSequence() {
        assertThat(ConfigUtils.isNotSet("test")).isFalse();
    }
}
