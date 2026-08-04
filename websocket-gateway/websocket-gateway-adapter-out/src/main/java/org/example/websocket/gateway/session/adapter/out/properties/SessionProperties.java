package org.example.websocket.gateway.session.adapter.out.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * STOMP 세션 위치 정보의 Redis TTL 설정.
 *
 * <p>연결 유휴 만료 정책이다. 구독마다 {@code refreshTtl} 로 갱신되므로 값이 짧으면
 * 활성 세션이 조기 만료될 수 있다.
 */
@ConfigurationProperties(prefix = "websocket.session")
public record SessionProperties(
        @DefaultValue("3m") Duration ttl
) {
}
