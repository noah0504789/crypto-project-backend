package org.example.chat.infra.redis;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.support.collections.DefaultRedisSet;
import org.springframework.data.redis.support.collections.DefaultRedisZSet;
import org.springframework.data.redis.support.collections.RedisCollection;
import org.springframework.data.redis.support.collections.RedisSet;
import org.springframework.data.redis.support.collections.RedisZSet;
import org.springframework.stereotype.Component;

@Component
public class RedisCollectionRegistry {

    private final RedisTemplate<String, String> masterRedisTemplate;
    private final RedisTemplate<String, String> replicaRedisTemplate;
    private final Cache<String, RedisCollection<String>> cache;

    public RedisCollectionRegistry(
            @Qualifier("masterHashRedisTemplate") RedisTemplate<String, String> masterRedisTemplate,
            @Qualifier("replicaHashRedisTemplate") RedisTemplate<String, String> replicaRedisTemplate,
            Cache<String, RedisCollection<String>> cache
    ) {
        this.masterRedisTemplate = masterRedisTemplate;
        this.replicaRedisTemplate = replicaRedisTemplate;
        this.cache = cache;
    }

    public RedisSet<String> getMasterSet(String key) {
        return (RedisSet<String>) cache.get(
                "set:" + key,
                k -> new DefaultRedisSet<>(key, masterRedisTemplate)
        );
    }

    public RedisZSet<String> getMasterZSet(String key) {
        return (RedisZSet<String>) cache.get(
                "zset:" + key,
                k -> new DefaultRedisZSet<>(key, masterRedisTemplate)
        );
    }

    public RedisZSet<String> getReplicaZSet(String key) {
        return (RedisZSet<String>) cache.get(
                "zset:" + key,
                k -> new DefaultRedisZSet<>(key, replicaRedisTemplate)
        );
    }
}