package org.example.websocket.gateway.chatmessage.adapter.out.stomp;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 기본값을 코드에 두지 않는다. 키 오타·누락 시 기동을 실패시켜 설정 오류를 배포 시점에 드러낸다.
@Validated
@ConfigurationProperties(prefix = "websocket.chat-message.batch")
public record ChatMessageBatchProperties(
        @NotNull Boolean enabled,

        // 방 하나에 대해 이 시간 안에 들어온 메시지를 한 프레임으로 묶는다.
        // 창을 키울수록 프레임 수는 줄고 수신 지연은 늘어난다.
        // 뱃지 conflation(200ms)보다 짧게 잡는다 — 메시지는 사용자가 기다리는 내용이다.
        @Positive Long windowMs,

        // 방당 버퍼 상한. 넘으면 창이 닫히기 전에 즉시 내보낸다.
        // conflation 과 달리 배칭은 한 건도 버리지 않으므로 버퍼가 유입량만큼 커진다.
        // 버리는 대신 일찍 내보내 메모리를 묶어둔다.
        @Positive Integer maxBatchSize
) {
}
