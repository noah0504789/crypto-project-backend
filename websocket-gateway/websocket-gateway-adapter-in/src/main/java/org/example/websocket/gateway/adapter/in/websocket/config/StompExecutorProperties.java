package org.example.websocket.gateway.adapter.in.websocket.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 기본값을 코드에 두지 않는다. 키 오타·누락 시 기동을 실패시켜 설정 오류를 배포 시점에 드러낸다.
@Validated
@ConfigurationProperties(prefix = "websocket.stomp.executor")
public record StompExecutorProperties(
        @Valid @NotNull Pool inbound,
        @Valid @NotNull Pool broker,
        @Valid @NotNull Pool outbound,
        @Valid @NotNull Pool ack
) {

    // core == max 로 운용한다. ThreadPoolExecutor 는 큐가 가득 찬 뒤에야 core 를 넘어 스레드를 늘리므로,
    // 큐를 키우면서 core 를 낮게 두면 스레드가 core 에 머물러 max 가 사실상 죽는다.
    public record Pool(
            @Positive Integer coreSize,
            @Positive Integer maxSize,
            @Positive Integer queueCapacity
    ) {
    }
}
