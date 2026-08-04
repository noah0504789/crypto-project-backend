package org.example.chat.chatmessage.client.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** chatmessage.v1 gRPC 호출 deadline. */
@ConfigurationProperties(prefix = "app.grpc.chat-client")
public record GrpcChatMessageClientProperties(@DefaultValue("10s") Duration deadline) {

    public long deadlineMillis() {
        return deadline.toMillis();
    }
}
