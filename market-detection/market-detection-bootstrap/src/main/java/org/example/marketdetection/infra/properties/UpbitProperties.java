package org.example.marketdetection.infra.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "upbit")
@Validated
public record UpbitProperties(@Valid @NotNull Websocket websocket, Ticker ticker, Store store) {

    public record Websocket(
            String url,
            String ticket,
            Duration tickerPublishInterval,
            @Positive int tickerReadyQueueCapacity,
            @Positive int tickerWorkerCount) {}

    public record Ticker(Alert alert) {
        public record Alert(int windowMinutes, Duration maxEventAge) {
            public Duration windowDuration() {
                return Duration.ofMinutes(windowMinutes);
            }
        }
    }

    public record Store(StoreTicker ticker) {
        public record StoreTicker(
                String name, Duration retention, Duration windowSize, boolean retainDuplicates) {}
    }
}
