package org.example.websocket.gateway.session.adapter.out.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * STOMP 세션 위치 Redis TTL. 연결 유휴 만료 정책이며 subscribe 마다 갱신된다 —
 * 짧으면 활성 세션이 조기 만료된다.
 */
@Validated
@ConfigurationProperties(prefix = "websocket.session")
public record SessionProperties(@NotNull Duration ttl) {
}
