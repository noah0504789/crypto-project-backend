package org.example.common.redis;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class StringRedisHashOperations {

    private final HashOperations<String, String, String> hashOps;

    public StringRedisHashOperations(RedisTemplate<String, String> redisTemplate) {
        this.hashOps = redisTemplate.opsForHash();
    }

    public void save(String key, Map<String, String> values) {
        hashOps.putAll(key, values);
    }

    public Map<String, String> find(String key) {
        return new HashMap<>(hashOps.entries(key));
    }

    public boolean hasEntries(String key) {
        return !find(key).isEmpty();
    }

    public String findField(String key, String field) {
        return hashOps.get(key, field);
    }

    public void update(String key, String field, String value) {
        hashOps.put(key, field, value == null ? "" : value);
    }

    public void update(String key, Map<String, String> updated) {
        hashOps.putAll(key, updated);
    }

    public void deleteField(String key, String field) {
        hashOps.delete(key, field);
    }

    public Long size(String key) {
        return hashOps.size(key);
    }

    public List<String> values(String key) {
        return hashOps.values(key);
    }
}
