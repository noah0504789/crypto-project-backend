package org.example.notification.infra.config;

import io.lettuce.core.ReadFrom;
import io.lettuce.core.resource.ClientResources;
import org.example.common.properties.AppRedisProperties;
import org.example.common.redis.support.RedisConnectionFactorySupport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * notification 서비스 Redis 설정.
 *
 * <p>알림 정보만 캐싱하므로 replica 라우팅 없이 master 커넥션만 사용한다.
 * cluster 커넥션/템플릿 생성은 {@link RedisConnectionFactorySupport} 공통 로직을 재사용한다.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory masterRedisConnectionFactory(
            ClientResources clientResources,
            RedisProperties redisProperties,
            AppRedisProperties appRedisProperties
    ) {
        return RedisConnectionFactorySupport.createClusterConnectionFactory(
                clientResources, redisProperties, appRedisProperties, ReadFrom.MASTER
        );
    }

    @Bean("redisTemplate")
    @Primary
    public RedisTemplate<String, String> redisTemplate(
            @Qualifier("masterRedisConnectionFactory") RedisConnectionFactory masterRedisConnectionFactory
    ) {
        return RedisConnectionFactorySupport.createStringRedisTemplate(masterRedisConnectionFactory);
    }

    @Bean("masterHashRedisTemplate")
    public RedisTemplate<String, String> masterHashRedisTemplate(
            @Qualifier("masterRedisConnectionFactory") RedisConnectionFactory masterRedisConnectionFactory
    ) {
        return RedisConnectionFactorySupport.createStringRedisTemplate(masterRedisConnectionFactory);
    }

    @Bean("warmUpNotification_lua")
    public RedisScript<Boolean> warmUpNotification() {
        return RedisScript.of(new ClassPathResource("META-INF/scripts/warmUpNotification.lua"), Boolean.class);
    }

    @Bean("invalidateNotification_lua")
    public RedisScript<Boolean> invalidateNotification() {
        return RedisScript.of(new ClassPathResource("META-INF/scripts/invalidateNotification.lua"), Boolean.class);
    }
}
