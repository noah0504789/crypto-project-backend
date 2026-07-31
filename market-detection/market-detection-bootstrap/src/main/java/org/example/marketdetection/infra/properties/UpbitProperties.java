package org.example.marketdetection.infra.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "upbit")
public record UpbitProperties(
        Websocket websocket,
        Ticker ticker,
        Store store
) {

    public record Websocket(
        String url,
        String ticket,
        Duration tickerPublishInterval,
        int tickerQueueCapacity
    ) {
    }

    public record Ticker(Alert alert) {
        public record Alert(
                int windowMinutes,
                Duration maxEventAge
        ) {
            public Duration windowDuration() {
                return Duration.ofMinutes(windowMinutes);
            }
        }
    }

    public record Store(StoreTicker ticker) {
        public record StoreTicker(
            String name,
            Duration retention,
            Duration windowSize,
            boolean retainDuplicates
        ) {
        }
    }
}
