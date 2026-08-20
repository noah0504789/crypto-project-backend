package org.example.upbitconnector.application.properties;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UpbitPropertiesUnitTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("WebSocket 시간 설정은 모두 양수이고 최대 재연결 간격이 최소 간격 이상이어야 한다")
    void websocketDurationSettingsMustBeValid() {
        assertThat(validator.validate(websocket(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(30))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("tickerPublishIntervalPositive");

        assertThat(validator.validate(websocket(
                        Duration.ofSeconds(-1), Duration.ofSeconds(1), Duration.ofSeconds(30))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("tickerPublishIntervalPositive");

        assertThat(validator.validate(websocket(Duration.ofSeconds(7), Duration.ZERO, Duration.ofSeconds(30))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("reconnectBackoffValid");

        assertThat(validator.validate(websocket(Duration.ofSeconds(7), Duration.ofSeconds(30), Duration.ofSeconds(1))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("reconnectBackoffValid");

        assertThat(validator.validate(websocket(Duration.ofSeconds(7), Duration.ofSeconds(1), Duration.ofSeconds(30))))
                .isEmpty();
    }

    private UpbitProperties.Websocket websocket(
            Duration tickerPublishInterval, Duration reconnectMinBackoff, Duration reconnectMaxBackoff) {
        return new UpbitProperties.Websocket(
                "wss://example.invalid/websocket/v1",
                "test",
                tickerPublishInterval,
                reconnectMinBackoff,
                reconnectMaxBackoff);
    }
}
