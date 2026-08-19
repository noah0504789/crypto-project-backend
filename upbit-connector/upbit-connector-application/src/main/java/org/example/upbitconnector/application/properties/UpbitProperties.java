package org.example.upbitconnector.application.properties;

import jakarta.validation.Valid;
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
            @NotNull Duration reconnectMaxBackoff) {}
}
