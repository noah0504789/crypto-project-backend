package org.example.infra.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.support.collections.RedisCollection;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory masterRedisConnectionFactory(ClientResources clientResources, RedisProperties redisProperties, AppRedisProperties appRedisProperties) {
        return RedisConnectionFactorySupport.createClusterConnectionFactory(clientResources, redisProperties, appRedisProperties, ReadFrom.MASTER);
    }

    @Bean
    public RedisConnectionFactory replicaRedisConnectionFactory(ClientResources clientResources, RedisProperties redisProperties, AppRedisProperties appRedisProperties) {
        return RedisConnectionFactorySupport.createClusterConnectionFactory(clientResources, redisProperties, appRedisProperties, ReadFrom.REPLICA_PREFERRED);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(
            @Qualifier("masterRedisConnectionFactory") RedisConnectionFactory masterRedisConnectionFactory) {
        return new StringRedisTemplate(masterRedisConnectionFactory);
    }

    @Bean("redisTemplate")
    @Primary
    public RedisTemplate<String, String> redisTemplate(
            @Qualifier("masterRedisConnectionFactory") RedisConnectionFactory cf
    ) {
        return RedisConnectionFactorySupport.createStringRedisTemplate(cf);
    }

    @Bean("masterHashRedisTemplate")
    public RedisTemplate<String, String> masterHashRedisTemplate(
            @Qualifier("masterRedisConnectionFactory") RedisConnectionFactory masterRedisConnectionFactory
    ) {
        return RedisConnectionFactorySupport.createStringRedisTemplate(masterRedisConnectionFactory);
    }

    @Bean("replicaHashRedisTemplate")
    public RedisTemplate<String, String> replicaHashRedisTemplate(
            @Qualifier("replicaRedisConnectionFactory") RedisConnectionFactory replicaRedisConnectionFactory
    ) {
        return RedisConnectionFactorySupport.createStringRedisTemplate(replicaRedisConnectionFactory);
    }

    @Bean
    public Cache<String, RedisCollection<String>> redisCollectionCache() {
       return Caffeine.newBuilder()
               .maximumSize(1_000) // TODO: 조절하기
               .expireAfterAccess(Duration.ofDays(3)) // TODO: 조절하기
               .build();
    }

    @Bean("storeChatRoom_lua")
    public RedisScript<Boolean> storeChatRoom() {
        return RedisScript.of(new ClassPathResource("META-INF/scripts/storeChatRoom.lua"), Boolean.class);
    }

    @Bean("warmUpChatRoom_lua")
    public RedisScript<Boolean> warmUpChatRoom() {
        return RedisScript.of(new ClassPathResource("META-INF/scripts/warmUpChatRoom.lua"), Boolean.class);
    }

    @Bean("warmUpChatRoomList_lua")
    public RedisScript<Boolean> warmUpChatRoomList() {
        return RedisScript.of(new ClassPathResource("META-INF/scripts/warmUpChatRoomList.lua"), Boolean.class);
    }

    @Bean("updateChatRoom_lua")
    public RedisScript<Boolean> updateChatRoom() {
        return RedisScript.of(new ClassPathResource("META-INF/scripts/updateChatRoom.lua"), Boolean.class);
    }

    @Bean("joinChatRoom_lua")
    public RedisScript<Boolean> joinChatRoom() {
        return RedisScript.of(new ClassPathResource("META-INF/scripts/joinChatRoom.lua"), Boolean.class);
    }

    @Bean("leaveChatRoom_lua")
    public RedisScript<Boolean> leaveChatRoom() {
        return RedisScript.of(new ClassPathResource("META-INF/scripts/leaveChatRoom.lua"), Boolean.class);
    }

    @Bean("deleteChatRoom_lua")
    public RedisScript<Boolean> deleteChatRoom() {
        return RedisScript.of(new ClassPathResource("META-INF/scripts/deleteChatRoom.lua"), Boolean.class);
    }

    @Bean("storeChatMessage_lua")
    public RedisScript<Boolean> storeChatMessage() {
        return RedisScript.of(new ClassPathResource("META-INF/scripts/storeChatMessage.lua"), Boolean.class);
    }

    @Bean("warmUpChatMessageList_lua")
    public RedisScript<Boolean> warmUpChatMessageList() {
        return RedisScript.of(new ClassPathResource("META-INF/scripts/storeChatMessageList.lua"), Boolean.class);
    }

    @Bean("deleteChatMessage_lua")
    public RedisScript<Long> deleteChatMessage() {
        return RedisScript.of(new ClassPathResource("META-INF/scripts/deleteChatMessage.lua"), Long.class);
    }

    @Bean("recoverUpdateChatRoom_lua")
    public RedisScript<Boolean> recoverUpdateChatRoom() {
        return RedisScript.of(new ClassPathResource("META-INF/scripts/recoverUpdateChatRoom.lua"), Boolean.class);
    }

    @Bean("invalidateChatRoomActivity_lua")
    public RedisScript<Boolean> invalidateChatRoomActivity() {
        return RedisScript.of(new ClassPathResource("META-INF/scripts/invalidateChatRoomActivity.lua"), Boolean.class);
    }

    @Bean("invalidateChatRoomInfo_lua")
    public RedisScript<Boolean> invalidateChatRoomInfo() {
        return RedisScript.of(new ClassPathResource("META-INF/scripts/invalidateChatRoomInfo.lua"), Boolean.class);
    }

}
