package de.medizininformatikinitiative.torch.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class InclusionConsentCheckPropertiesTest {

    static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    static InclusionConsentCheckProperties properties(Duration capabilityStatementRefresh) {
        return new InclusionConsentCheckProperties(true, "mapping_fhir.json", "type_to_consent_search_param.json", 1, capabilityStatementRefresh);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT24H", "PT1S", "PT0.001S"})
    void acceptsPositiveCapabilityStatementRefresh(String refresh) {
        assertThat(VALIDATOR.validate(properties(Duration.parse(refresh)))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-PT1H"})
    void rejectsNonPositiveCapabilityStatementRefresh(String refresh) {
        assertThat(VALIDATOR.validate(properties(Duration.parse(refresh))))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Capability statement refresh must be positive");
    }

    @Test
    void rejectsMissingCapabilityStatementRefresh() {
        assertThat(VALIDATOR.validate(properties(null))).isNotEmpty();
    }
}
