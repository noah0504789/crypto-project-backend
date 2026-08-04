package org.example.market.client.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** market.v1 gRPC 호출 deadline(Market·PriceAlertSetting 공용). */
@ConfigurationProperties(prefix = "app.grpc.market-client")
public record GrpcMarketClientProperties(@DefaultValue("3500ms") Duration deadline) {

    public long deadlineMillis() {
        return deadline.toMillis();
    }
}
