package de.medizininformatikinitiative.torch.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigUtilsTest {
    
    @Nested
    class TestEmptyFields {

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
}
