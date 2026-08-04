package org.example.notification.infra.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * notification Redis 캐시의 TTL 설정.
 *
 * <p>알림 정보는 불변이라 TTL 은 "만료 방어"가 아니라 콜드 항목 상한(안전망) 역할이다.
 * 실제 교체는 Redis 서버의 LFU 축출({@code maxmemory-policy}, 인프라 설정)이 담당한다.
 */
@ConfigurationProperties(prefix = "notification.cache")
public record NotificationCacheProperties(
        @DefaultValue("7d") Duration ttl
) {

    /** Lua 스크립트 인자로 넘기는 TTL(초 문자열). */
    public String ttlSeconds() {
        return String.valueOf(ttl.toSeconds());
    }
}
