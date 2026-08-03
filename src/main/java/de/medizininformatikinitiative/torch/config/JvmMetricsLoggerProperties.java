package de.medizininformatikinitiative.torch.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "torch.jvm-metrics-logger")
@Validated
public record JvmMetricsLoggerProperties(
        @NotNull(message = "Interval is required") Duration interval,
        @Min(value = 1, message = "Warn factor must be at least 1")
        @Max(value = 999, message = "Warn factor must be at most 999") int warnFactor,
        @Min(value = 1, message = "Warn threshold must be at least 1")
        @Max(value = 99, message = "Warn threshold must be at most 99") int warnThreshold
) {
}
