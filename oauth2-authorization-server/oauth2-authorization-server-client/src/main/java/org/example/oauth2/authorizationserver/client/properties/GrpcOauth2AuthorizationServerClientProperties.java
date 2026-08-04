package org.example.oauth2.authorizationserver.client.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** auth.v1 gRPC 호출 deadline(Access·Refresh·Blacklist·AuthorizedClient 공용). */
@ConfigurationProperties(prefix = "app.grpc.oauth2-authorization-server-client")
public record GrpcOauth2AuthorizationServerClientProperties(@DefaultValue("3500ms") Duration deadline) {

    public long deadlineMillis() {
        return deadline.toMillis();
    }
}
