package org.example.user.client.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** user.v1 gRPC 호출 deadline. */
@Validated
@ConfigurationProperties(prefix = "app.grpc.user-client")
public record GrpcUserClientProperties(@NotNull Duration deadline) {

    public long deadlineMillis() {
        return deadline.toMillis();
    }
}
