package org.example.marketdetection.application.properties;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PriceAlertDetectionPropertiesUnitTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("탐지 시간 설정은 양수이고 store retention이 모든 window를 포함해야 한다")
    void detectionDurationSettingsMustBeValid() {
        assertThat(validator.validate(properties(0, Duration.ofSeconds(10), minutes(5), minutes(3))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("windowMinutes");

        assertThat(validator.validate(properties(3, Duration.ZERO, minutes(5), minutes(3))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("maxEventAgePositive");

        assertThat(validator.validate(properties(3, Duration.ofSeconds(10), Duration.ZERO, minutes(3))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("storeDurationValid");

        assertThat(validator.validate(properties(3, Duration.ofSeconds(10), minutes(2), minutes(3))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("storeDurationValid");

        assertThat(validator.validate(properties(5, Duration.ofSeconds(10), minutes(3), minutes(3))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("storeDurationValid");

        assertThat(validator.validate(properties(3, Duration.ofSeconds(10), minutes(5), minutes(3))))
                .isEmpty();
    }

    @Test
    @DisplayName("retention이 탐지 window와 같으면 여유가 0이라 거부한다")
    void retentionEqualToDetectionWindowIsRejected() {
        assertThat(validator.validate(properties(3, Duration.ofSeconds(10), minutes(3), minutes(3))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("storeDurationValid");
    }

    @Test
    @DisplayName("retention이 탐지 window + 최소 여유면 통과한다")
    void retentionAtMinimumMarginIsAccepted() {
        Duration atFloor = minutes(3).plus(PriceAlertDetectionProperties.RETENTION_MIN_MARGIN);

        assertThat(validator.validate(properties(3, Duration.ofSeconds(10), atFloor, minutes(3))))
                .isEmpty();
    }

    private PriceAlertDetectionProperties properties(
            int windowMinutes, Duration maxEventAge, Duration retention, Duration windowSize) {
        return new PriceAlertDetectionProperties(
                windowMinutes,
                maxEventAge,
                new PriceAlertDetectionProperties.Store("upbit-ticker-store", retention, windowSize, false));
    }

    private Duration minutes(long minutes) {
        return Duration.ofMinutes(minutes);
    }
}
