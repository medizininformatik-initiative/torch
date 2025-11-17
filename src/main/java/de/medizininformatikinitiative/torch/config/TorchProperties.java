package de.medizininformatikinitiative.torch.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "torch")
@Validated
public record TorchProperties(
        @Valid Base base,
        @Valid Output output,
        @Valid Profile profile,
        @Valid Mapping mapping,
        @Valid Flare flare,
        @Valid Results results,
        @Min(value = 1, message = "Batch size must be at least 1") int batchsize,
        @Min(value = 1, message = "Max concurrency must be at least 1") int maxConcurrency,
        @Min(value = 1, message = "Buffer size must be at least 1") int bufferSize,
        @NotBlank(message = "Mappings file path is required") String mappingsFile,
        @NotBlank(message = "Concept tree file path is required") String conceptTreeFile,
        @NotBlank(message = "DSE mapping tree file path is required") String dseMappingTreeFile,
        @NotBlank(message = "Search Parameters file is required") String searchParametersFile,
        boolean useCql
) {
    private static final String EMPTY_QUOTES = "\"\"";

    public static boolean isNotSet(String variable) {
        return variable == null || variable.isBlank() || EMPTY_QUOTES.equals(variable);
    }

    public TorchProperties {
        if (!useCql) {
            if (flare == null) {
                throw new IllegalArgumentException("When useCql is false, flare.url must be a non-empty string");
            }
            if (isNotSet(flare.url())) {
                throw new IllegalArgumentException("When useCql is false, flare.url must be a non-empty string");
            }
        }
    }

    public record Base(@NotBlank(message = "Base URL is required") String url) {
    }

    public record Max(@Min(value = 1, message = "Max connections must be at least 1") int connections) {
    }

    public record Output(@Valid @NotNull(message = "File configuration is required") File file) {
        public record File(@Valid @NotNull(message = "Server configuration is required") Server server) {
            public record Server(@NotBlank(message = "Output server URL is required") String url) {
            }
        }
    }

    public record Profile(@NotBlank(message = "Profile directory is required") String dir) {
    }

    public record Mapping(
            @NotBlank(message = "Consent mapping is required") String consent,
            @NotBlank(message = "Type to consent mapping is required") String typeToConsent) {
    }


    public record Flare(String url) {
    }

    public record Results(
            @NotBlank(message = "Results directory is required") String dir,
            @NotBlank(message = "Results persistence configuration is required") String persistence) {
    }
}
