package org.example.chat.infra.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * chat Redis 캐시 보존 기간. 대상이 달라 별도 항목이다 —
 * roomTtl 은 방 hash 의 Redis TTL, messageRetention 은 스케줄러의 메시지 제거 기준 나이.
 */
@ConfigurationProperties(prefix = "chat.cache")
public record ChatCacheProperties(@DefaultValue("7d") Duration roomTtl, @DefaultValue("7d") Duration messageRetention) {

    public String roomTtlSeconds() {
        return String.valueOf(roomTtl.toSeconds());
    }
}
