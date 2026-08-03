package org.example.notification.adapter.out.cache;

import org.example.common.redis.codec.RedisHashCodec;
import org.example.common.redis.failover.CacheFailOpen;
import org.example.common.redis.operation.StringRedisHashOperations;
import org.example.notification.application.port.out.NotificationCachePort;
import org.example.notification.domain.model.Notification;
import org.example.notification.exception.NotificationCacheException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.example.common.enums.RedisKey.NOTIFICATION_INFO;

/**
 * Notification 정보의 Redis 1차 캐시 어댑터.
 *
 * <p>알림 정보는 <b>불변</b>이라 stale 이 없다 → PER·SWR·락 없이 <b>선적재 + 긴 TTL</b>로만 운영한다.
 * 콜드 항목은 Redis <b>LFU 축출</b>(서버 {@code maxmemory-policy}, 인프라 설정)로 자동 교체된다.
 * 인덱스는 캐싱하지 않고 알림 정보(id→hash) 만 캐싱한다. 조회 경로는 {@code @CacheFailOpen} 으로 fail-open.
 */
@Repository
public class RedisNotificationAdapter implements NotificationCachePort {

    // 불변 데이터라 TTL 은 "만료 방어"가 아니라 콜드 항목 상한(안전망) 역할. 실제 교체는 LFU 축출이 담당.
    private static final String CACHE_TTL_SECONDS = String.valueOf(Duration.ofDays(7).toSeconds());

    private final RedisTemplate<String, String> masterHashRedisTemplate;
    private final StringRedisHashOperations hash;
    private final RedisHashCodec<RedisNotification> codec;
    private final RedisScript<Boolean> warmUpNotification_lua;
    private final RedisScript<Boolean> invalidateNotification_lua;

    public RedisNotificationAdapter(
            @Qualifier("masterHashRedisTemplate") RedisTemplate<String, String> masterHashRedisTemplate,
            StringRedisHashOperations hash,
            @Qualifier("redisNotificationCodec") RedisHashCodec<RedisNotification> codec,
            @Qualifier("warmUpNotification_lua") RedisScript<Boolean> warmUpNotification_lua,
            @Qualifier("invalidateNotification_lua") RedisScript<Boolean> invalidateNotification_lua
    ) {
        this.masterHashRedisTemplate = masterHashRedisTemplate;
        this.hash = hash;
        this.codec = codec;
        this.warmUpNotification_lua = warmUpNotification_lua;
        this.invalidateNotification_lua = invalidateNotification_lua;
    }

    @Override
    @CacheFailOpen
    public Map<String, Notification> findByIds(Set<String> ids) {
        Map<String, Notification> result = new LinkedHashMap<>();

        if (ids == null || ids.isEmpty()) {
            return result;
        }

        for (String id : ids) {
            if (id == null || result.containsKey(id)) {
                continue;
            }

            Map<String, String> source = hash.find(NOTIFICATION_INFO.keyFor(id));

            if (source == null || source.isEmpty()) {
                continue;
            }

            RedisNotification cached = codec.read(source);

            if (cached != null) {
                result.put(id, cached.toDomain());
            }
        }

        return result;
    }

    @Override
    public void warmUpAll(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return;
        }

        for (Notification notification : notifications) {
            warmUp(notification);
        }
    }

    @Override
    public void invalidate(String id) {
        if (id == null || id.isBlank()) {
            return;
        }

        String infoKey = NOTIFICATION_INFO.keyFor(id);

        if (!masterHashRedisTemplate.execute(invalidateNotification_lua, List.of(infoKey), id)) {
            throw new NotificationCacheException("[redis] notification invalidate() failed!");
        }
    }

    private void warmUp(Notification notification) {
        String infoKey = NOTIFICATION_INFO.keyFor(notification.getId());

        Map<String, String> fields = codec.write(RedisNotification.fromDomain(notification));

        List<String> args = new ArrayList<>(fields.size() * 2 + 2);
        args.add(String.valueOf(fields.size()));
        fields.forEach((field, value) -> {
            args.add(field);
            args.add(value == null ? "" : value);
        });
        args.add(CACHE_TTL_SECONDS);

        if (!masterHashRedisTemplate.execute(warmUpNotification_lua, List.of(infoKey), args.toArray())) {
            throw new NotificationCacheException("[redis] notification warmUp() failed! id=" + notification.getId());
        }
    }
}
