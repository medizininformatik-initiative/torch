package de.medizininformatikinitiative.torch.consent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsentViolatedExceptionTest {

    @Test
    void preservesMessage() {
        ConsentViolatedException exception = new ConsentViolatedException("no consenting patients");

        assertThat(exception).hasMessage("no consenting patients");
    }
}
