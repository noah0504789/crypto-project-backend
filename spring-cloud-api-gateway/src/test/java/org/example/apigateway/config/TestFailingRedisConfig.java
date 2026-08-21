package org.example.apigateway.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
public class TestFailingRedisConfig {

    @Bean
    @Primary
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ReactiveStringRedisTemplate failingReactiveStringRedisTemplate() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn((Flux) Flux.error(new RedisConnectionFailureException("Redis unavailable")));
        return redisTemplate;
    }
}
