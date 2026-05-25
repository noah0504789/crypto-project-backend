package org.example.oauth2.authorizationserver.infra.redis;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.support.collections.DefaultRedisSet;
import org.springframework.data.redis.support.collections.RedisSet;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisSetRegistry {

    private final StringRedisTemplate stringRedisTemplate;
    private final Cache<String, RedisSet<String>> cache;

    public RedisSet<String> getSet(String key) {
        return cache.get(key, k -> new DefaultRedisSet<>(k, stringRedisTemplate));
    }
}