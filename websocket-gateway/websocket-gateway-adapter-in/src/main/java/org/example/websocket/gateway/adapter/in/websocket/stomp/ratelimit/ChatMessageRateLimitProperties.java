package org.example.websocket.gateway.adapter.in.websocket.stomp.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 기본값을 코드에 두지 않는다. 키 오타·누락 시 기동을 실패시켜 설정 오류를 배포 시점에 드러낸다.
@Validated
@ConfigurationProperties(prefix = "app.rate-limit.chat-message")
public record ChatMessageRateLimitProperties(
        @NotNull Boolean enabled,
        @Valid @NotNull Bucket user,
        @Valid @NotNull Bucket room
) {

    public record Bucket(
            @Positive long replenishRate,
            @Positive long burstCapacity
    ) {
    }
}
