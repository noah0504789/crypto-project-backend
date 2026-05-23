package org.example.common.redis.codec;

public interface RedisValueCodec<T> {

    String write(T value);

    T read(String source);
}
