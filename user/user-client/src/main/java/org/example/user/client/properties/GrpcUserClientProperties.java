package org.example.user.client.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** user.v1 gRPC 호출 deadline. */
@ConfigurationProperties(prefix = "app.grpc.user-client")
public record GrpcUserClientProperties(@DefaultValue("3500ms") Duration deadline) {

    public long deadlineMillis() {
        return deadline.toMillis();
    }
}
