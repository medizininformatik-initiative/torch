package de.medizininformatikinitiative.torch.model.consent;

import de.medizininformatikinitiative.torch.model.management.TermCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class TermCodeTest {

    @Test
    void validConsentCodeShouldBeCreated() {
        TermCode consentCode = new TermCode("systemA", "code123");

        assertThat(consentCode.system()).isEqualTo("systemA");
        assertThat(consentCode.code()).isEqualTo("code123");
    }

    @Test
    void nullSystemShouldThrowException() {
        assertThatThrownBy(() -> new TermCode(null, "code123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("system must not be null or blank");
    }

    @Test
    void blankSystemShouldThrowException() {
        assertThatThrownBy(() -> new TermCode("   ", "code123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("system must not be null or blank");
    }

    @Test
    void nullCodeShouldThrowException() {
        assertThatThrownBy(() -> new TermCode("systemA", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("code must not be null or blank");
    }

    @Test
    void blankCodeShouldThrowException() {
        assertThatThrownBy(() -> new TermCode("systemA", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("code must not be null or blank");
    }
}
