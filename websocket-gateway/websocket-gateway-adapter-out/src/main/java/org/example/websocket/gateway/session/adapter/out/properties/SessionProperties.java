package org.example.websocket.gateway.session.adapter.out.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * STOMP 세션 위치 Redis TTL. 연결 유휴 만료 정책이며 subscribe 마다 갱신된다 —
 * 짧으면 활성 세션이 조기 만료된다.
 */
@ConfigurationProperties(prefix = "websocket.session")
public record SessionProperties(@DefaultValue("3m") Duration ttl) {
}
