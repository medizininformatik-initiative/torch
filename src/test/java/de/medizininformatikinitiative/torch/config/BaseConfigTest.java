package de.medizininformatikinitiative.torch.config;

import ca.uhn.fhir.context.FhirContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BaseConfigTest {

    static TorchProperties typeToConsent(Path file) {
        var properties = mock(TorchProperties.class);
        when(properties.mapping()).thenReturn(new TorchProperties.Mapping(file.toString()));
        return properties;
    }

    @Test
    void consentValidatorLoadsTheShippedMapping() throws Exception {
        var validator = new BaseConfig().consentValidator(FhirContext.forR4(), new ObjectMapper(),
                typeToConsent(Path.of("mappings/type_to_consent.json")));

        assertThat(validator.consentDateMapping("Condition")).hasValueSatisfying(mapping ->
                assertThat(mapping.dateSearchParam()).isEqualTo("recorded-date"));
    }

    @Test
    void consentValidatorRejectsTheFormerStringFormat(@TempDir Path dir) throws Exception {
        var file = Files.writeString(dir.resolve("type_to_consent.json"),
                "{\"Condition\": \"Condition.recordedDate\", \"Patient\": \"\"}");

        assertThatThrownBy(() -> new BaseConfig().consentValidator(FhirContext.forR4(), new ObjectMapper(), typeToConsent(file)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("each resource type has to map to an object with dateField and dateSearchParam");
    }
}
