package de.medizininformatikinitiative.torch.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InclusionConsentCheckConfigTest {

    @Test
    void consentSearchParamsRejectsSearchParamsNotMatchingTheDateFields(@TempDir Path dir) throws Exception {
        var typeToConsent = Files.writeString(dir.resolve("type_to_consent.json"),
                "{\"Condition\": \"Condition.recordedDate\", \"Patient\": \"\"}");
        var searchParamsFile = Files.writeString(dir.resolve("type_to_consent_search_param.json"),
                "{\"Patient\": \"birthdate\"}");
        var torchProperties = mock(TorchProperties.class);
        when(torchProperties.mapping()).thenReturn(new TorchProperties.Mapping(typeToConsent.toString()));
        var properties = new InclusionConsentCheckProperties(true, "mapping_fhir.json", searchParamsFile.toString(), 1,
                Duration.ofHours(24));

        assertThatThrownBy(() -> new InclusionConsentCheckConfig().consentSearchParams(new ObjectMapper(),
                torchProperties, properties))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(searchParamsFile.toString())
                .hasMessageContaining("[Condition]")
                .hasMessageContaining("[Patient]");
    }
}
