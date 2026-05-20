package org.example.common.redis;

import java.util.Map;

public interface RedisHashCodec<T> {

    T read(Map<String, String> source);

    Map<String, String> write(T value);

    Map<String, String> writePartial(Map<String, Object> updated);
}