package de.medizininformatikinitiative.torch.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

/**
 * Checks that environment variables override the {@link TorchProperties} defined in the main {@code application.yml}.
 */
class EnvironmentVariableBindingTest {

    private static TorchProperties bindWith(Map<String, Object> variables) throws IOException {
        var environment = new StandardEnvironment();
        environment.getPropertySources().replace(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new SystemEnvironmentPropertySource(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
        new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        ConfigurationPropertySources.attach(environment);
        return Binder.get(environment).bindOrCreate("torch", TorchProperties.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TORCH_ENABLE_ENCOUNTER_SHIFT", "TORCH_ENABLEENCOUNTERSHIFT"})
    void encounterShiftCanBeDisabled(String variable) throws IOException {
        assertThat(bindWith(Map.of(variable, "false")).enableEncounterShift()).isFalse();
    }

    @Test
    void encounterShiftIsEnabledByDefault() throws IOException {
        assertThat(bindWith(Map.of()).enableEncounterShift()).isTrue();
    }
}
