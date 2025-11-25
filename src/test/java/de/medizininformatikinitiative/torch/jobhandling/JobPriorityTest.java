package de.medizininformatikinitiative.torch.jobhandling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobPriorityTest {

    @ParameterizedTest(name = "Value {0} should map to {1}")
    @CsvSource({
            "0, NORMAL",
            "1, HIGH"
    })
    @DisplayName("Verify valid integer to Enum mapping")
    void shouldMapValidValuesToEnum(int input, JobPriority expected) {
        // AssertJ provides a very natural 'isEqualTo' check for Enums
        assertThat(JobPriority.fromValue(input))
                .isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("Verify Enum to integer value mapping")
    void shouldReturnCorrectInternalValue() {
        assertThat(JobPriority.NORMAL.value()).isZero();
        assertThat(JobPriority.HIGH.value()).isEqualTo(1);
    }

    @Test
    @DisplayName("Verify exception thrown for unknown values")
    void shouldThrowExceptionOnUnknownValue() {
        int unknownValue = -1;

        assertThatThrownBy(() -> JobPriority.fromValue(unknownValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown job priority: %d", unknownValue);
    }
}
