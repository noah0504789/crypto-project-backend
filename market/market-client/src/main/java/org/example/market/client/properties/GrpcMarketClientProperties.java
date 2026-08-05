package org.example.market.client.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** market.v1 gRPC 호출 deadline(Market·PriceAlertSetting 공용). */
@Validated
@ConfigurationProperties(prefix = "app.grpc.market-client")
public record GrpcMarketClientProperties(@NotNull Duration deadline) {

    public long deadlineMillis() {
        return deadline.toMillis();
    }
}
