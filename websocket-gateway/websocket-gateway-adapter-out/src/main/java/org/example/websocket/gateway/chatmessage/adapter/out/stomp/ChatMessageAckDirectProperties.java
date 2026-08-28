package org.example.websocket.gateway.chatmessage.adapter.out.stomp;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 기본값을 코드에 두지 않는다. 키 오타·누락 시 기동을 실패시켜 설정 오류를 배포 시점에 드러낸다.
@Validated
@ConfigurationProperties(prefix = "websocket.chat-message.ack.direct")
public record ChatMessageAckDirectProperties(
        @NotNull Boolean enabled
) {
}
