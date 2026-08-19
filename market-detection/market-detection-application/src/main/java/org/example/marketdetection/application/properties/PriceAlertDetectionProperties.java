package org.example.marketdetection.application.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "price-alert-detection")
@Validated
public record PriceAlertDetectionProperties(
        @Positive Integer windowMinutes,
        @NotNull Duration maxEventAge,
        @Valid @NotNull Store store
) {

    public Duration windowDuration() {
        return Duration.ofMinutes(windowMinutes);
    }

    public record Store(
            @NotBlank String name,
            @NotNull Duration retention,
            @NotNull Duration windowSize,
            boolean retainDuplicates
    ) {}
}
