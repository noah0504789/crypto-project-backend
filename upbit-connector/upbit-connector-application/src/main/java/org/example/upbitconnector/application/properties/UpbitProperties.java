package org.example.upbitconnector.application.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "upbit")
@Validated
public record UpbitProperties(@Valid @NotNull Websocket websocket) {

    public record Websocket(
            @NotBlank String url,
            @NotBlank String ticket,
            @NotNull Duration tickerPublishInterval,
            @NotNull Duration reconnectMinBackoff,
            @NotNull Duration reconnectMaxBackoff) {

        @AssertTrue(message = "ticker-publish-interval must be positive")
        public boolean isTickerPublishIntervalPositive() {
            return tickerPublishInterval == null || isPositive(tickerPublishInterval);
        }

        @AssertTrue(message = "reconnect backoff must be positive and max must be greater than or equal to min")
        public boolean isReconnectBackoffValid() {
            return reconnectMinBackoff == null
                    || reconnectMaxBackoff == null
                    || (isPositive(reconnectMinBackoff)
                            && isPositive(reconnectMaxBackoff)
                            && reconnectMaxBackoff.compareTo(reconnectMinBackoff) >= 0);
        }

        private boolean isPositive(Duration duration) {
            return !duration.isZero() && !duration.isNegative();
        }
    }
}
