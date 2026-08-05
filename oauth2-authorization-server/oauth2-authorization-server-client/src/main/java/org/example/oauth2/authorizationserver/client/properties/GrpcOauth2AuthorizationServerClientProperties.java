package org.example.oauth2.authorizationserver.client.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** auth.v1 gRPC 호출 deadline(Access·Refresh·Blacklist·AuthorizedClient 공용). */
@Validated
@ConfigurationProperties(prefix = "app.grpc.oauth2-authorization-server-client")
public record GrpcOauth2AuthorizationServerClientProperties(@NotNull Duration deadline) {

    public long deadlineMillis() {
        return deadline.toMillis();
    }
}
