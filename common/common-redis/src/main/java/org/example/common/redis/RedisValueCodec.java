package org.example.common.redis;

public interface RedisValueCodec<T> {

    String write(T value);

    T read(String source);
}
