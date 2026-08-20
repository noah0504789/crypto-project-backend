package org.example.marketdetection.application.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "price-alert-detection")
@Validated
public record PriceAlertDetectionProperties(
        @NotNull @Positive Integer windowMinutes,
        @NotNull Duration maxEventAge,
        @Valid @NotNull Store store) {

    public Duration windowDuration() {
        return Duration.ofMinutes(windowMinutes);
    }

    @AssertTrue(message = "max-event-age must be positive")
    public boolean isMaxEventAgePositive() {
        return maxEventAge == null || isPositive(maxEventAge);
    }

    @AssertTrue(message = "store durations must be positive and retention must cover every window")
    public boolean isStoreDurationValid() {
        if (windowMinutes == null || store == null || store.retention() == null || store.windowSize() == null) {
            return true;
        }

        Duration detectionWindow = Duration.ofMinutes(windowMinutes);

        return isPositive(store.retention())
                && isPositive(store.windowSize())
                && store.retention().compareTo(store.windowSize()) >= 0
                && store.retention().compareTo(detectionWindow) >= 0;
    }

    private boolean isPositive(Duration duration) {
        return !duration.isZero() && !duration.isNegative();
    }

    public record Store(
            @NotBlank String name,
            @NotNull Duration retention,
            @NotNull Duration windowSize,
            boolean retainDuplicates) {}
}
