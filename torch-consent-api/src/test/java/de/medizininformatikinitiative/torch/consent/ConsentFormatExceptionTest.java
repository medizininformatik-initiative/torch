package de.medizininformatikinitiative.torch.consent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentFormatExceptionTest {

    @Test
    void preservesMessage() {
        ConsentFormatException exception = new ConsentFormatException("malformed consent criteria");

        assertThat(exception).hasMessage("malformed consent criteria");
    }
}
