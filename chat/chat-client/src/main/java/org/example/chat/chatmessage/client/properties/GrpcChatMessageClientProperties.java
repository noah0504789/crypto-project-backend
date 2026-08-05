package org.example.chat.chatmessage.client.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** chatmessage.v1 gRPC 호출 deadline. */
@Validated
@ConfigurationProperties(prefix = "app.grpc.chat-client")
public record GrpcChatMessageClientProperties(@NotNull Duration deadline) {

    public long deadlineMillis() {
        return deadline.toMillis();
    }
}
