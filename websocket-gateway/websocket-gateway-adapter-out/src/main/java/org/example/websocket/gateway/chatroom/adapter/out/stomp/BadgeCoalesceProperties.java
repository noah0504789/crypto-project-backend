package org.example.websocket.gateway.chatroom.adapter.out.stomp;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 기본값을 코드에 두지 않는다. 키 오타·누락 시 기동을 실패시켜 설정 오류를 배포 시점에 드러낸다.
// (StompExecutorProperties 와 같은 규칙)
@Validated
@ConfigurationProperties(prefix = "websocket.badge.coalesce")
public record BadgeCoalesceProperties(
        @NotNull Boolean enabled,

        // 방 하나에 대해 이 시간 안에 들어온 뱃지는 마지막 1건만 전송한다.
        // 창을 키울수록 brokerChannel 태스크는 줄고 뱃지 갱신 지연은 늘어난다.
        @Positive Long windowMs
) {
}
