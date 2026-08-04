package org.example.chat.infra.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * chat Redis 캐시의 보존 기간 설정.
 *
 * <p>두 값은 서로 다른 키를 대상으로 한다 — {@code roomTtl} 은 방 정보 hash 의 Redis TTL,
 * {@code messageRetention} 은 스케줄러가 메시지 캐시에서 제거할 기준 나이다. 기본값은 같지만
 * 대상이 달라 별도 항목으로 둔다.
 */
@ConfigurationProperties(prefix = "chat.cache")
public record ChatCacheProperties(
        @DefaultValue("7d") Duration roomTtl,
        @DefaultValue("7d") Duration messageRetention
) {

    /** Lua 스크립트 인자로 넘기는 TTL(초 문자열). */
    public String roomTtlSeconds() {
        return String.valueOf(roomTtl.toSeconds());
    }
}
