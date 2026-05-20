package org.example.infra.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.tracing.MicrometerTracing;
import io.micrometer.observation.ObservationRegistry;
import org.example.common.properties.AppRedisProperties;
import org.example.common.redis.RedisConnectionFactorySupport;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.support.collections.RedisSet;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Bean
    public ClientResources clientResources(ObservationRegistry observationRegistry) {
        return ClientResources.builder()
                .tracing(new MicrometerTracing(observationRegistry, "spring-data-redis"))
                .build();
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory(ClientResources clientResources, RedisProperties redisProperties, AppRedisProperties appRedisProperties) {
        return RedisConnectionFactorySupport.createClusterConnectionFactory(clientResources, redisProperties, appRedisProperties);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    public Cache<String, RedisSet<String>> redisSetCache() {
        return Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterAccess(Duration.ofDays(3))
                .build();
    }

    @Bean("storeRefreshToken_lua")
    public RedisScript<Boolean> storeRefreshToken() {
        return RedisScript.of(
                new ClassPathResource("META-INF/scripts/storeRefreshToken.lua"),
                Boolean.class
        );
    }

    @Bean("storeTokens_lua")
    public RedisScript<Boolean> storeTokens() {
        return RedisScript.of(
                new ClassPathResource("META-INF/scripts/storeTokens.lua"),
                Boolean.class
        );
    }

    @Bean("deleteTokens_lua")
    public RedisScript<Boolean> deleteTokens() {
        return RedisScript.of(
                new ClassPathResource("META-INF/scripts/deleteTokens.lua"),
                Boolean.class
        );
    }
}
