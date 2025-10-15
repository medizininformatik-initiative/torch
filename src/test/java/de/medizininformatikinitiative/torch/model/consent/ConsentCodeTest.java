package de.medizininformatikinitiative.torch.model.consent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class ConsentCodeTest {

    @Test
    void validConsentCodeShouldBeCreated() {
        ConsentCode consentCode = new ConsentCode("systemA", "code123");

        assertThat(consentCode.system()).isEqualTo("systemA");
        assertThat(consentCode.code()).isEqualTo("code123");
    }

    @Test
    void nullSystemShouldThrowException() {
        assertThatThrownBy(() -> new ConsentCode(null, "code123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("system must not be null or blank");
    }

    @Test
    void blankSystemShouldThrowException() {
        assertThatThrownBy(() -> new ConsentCode("   ", "code123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("system must not be null or blank");
    }

    @Test
    void nullCodeShouldThrowException() {
        assertThatThrownBy(() -> new ConsentCode("systemA", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("code must not be null or blank");
    }

    @Test
    void blankCodeShouldThrowException() {
        assertThatThrownBy(() -> new ConsentCode("systemA", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("code must not be null or blank");
    }
}
