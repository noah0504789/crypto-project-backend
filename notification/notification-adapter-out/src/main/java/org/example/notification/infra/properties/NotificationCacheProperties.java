package org.example.notification.infra.properties;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * notification Redis 캐시 TTL. 알림은 불변이라 만료 방어가 아니라 콜드 항목 상한이며,
 * 실제 교체는 Redis 서버 LFU 축출이 담당한다.
 */
@Validated
@ConfigurationProperties(prefix = "notification.cache")
public record NotificationCacheProperties(@NotNull Duration ttl) {

    public String ttlSeconds() {
        return String.valueOf(ttl.toSeconds());
    }
}
