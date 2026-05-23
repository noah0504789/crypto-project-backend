package config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.support.collections.RedisSet;

import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
public class TestRedisConfig {

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port
    ) {
        RedisStandaloneConfiguration standaloneConfig =
                new RedisStandaloneConfiguration(host, port);

        LettuceClientConfiguration clientConfig =
                LettuceClientConfiguration.builder()
                        .commandTimeout(Duration.ofSeconds(3))
                        .build();

        return new LettuceConnectionFactory(standaloneConfig, clientConfig);
    }

    @Bean
    @Primary
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