package org.example.websocket.adapter.out.config;

import io.lettuce.core.ReadFrom;
import io.lettuce.core.resource.ClientResources;
import org.example.common.properties.AppRedisProperties;
import org.example.common.redis.support.RedisConnectionFactorySupport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory masterRedisConnectionFactory(ClientResources clientResources, RedisProperties redisProperties, AppRedisProperties appRedisProperties) {
        return RedisConnectionFactorySupport.createClusterConnectionFactory(clientResources, redisProperties, appRedisProperties, ReadFrom.MASTER);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(
            @Qualifier("masterRedisConnectionFactory") RedisConnectionFactory masterRedisConnectionFactory) {
        return new StringRedisTemplate(masterRedisConnectionFactory);
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(
            @Qualifier("masterRedisConnectionFactory") RedisConnectionFactory cf
    ) {
        return RedisConnectionFactorySupport.createStringRedisTemplate(cf);
    }
}
