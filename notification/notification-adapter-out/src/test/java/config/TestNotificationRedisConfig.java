package config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.redis.codec.RedisHashCodec;
import org.example.common.redis.operation.StringRedisHashOperations;
import org.example.notification.adapter.out.cache.RedisNotification;
import org.example.notification.adapter.out.cache.RedisNotificationAdapter;
import org.example.notification.adapter.out.cache.RedisNotificationCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * notification 캐시 어댑터 통합 테스트용 wiring.
 * 운영 {@code RedisConfig}는 cluster 커넥션이라, 단일 노드 Testcontainer(redis:7.2.0)에 맞춰
 * 커넥션/템플릿/스크립트/코덱/어댑터를 수동 구성한다.
 */
@TestConfiguration
public class TestNotificationRedisConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Primary
    @Bean("masterRedisConnectionFactory")
    public RedisConnectionFactory masterRedisConnectionFactory(Environment env) {
        String host = env.getProperty("spring.data.redis.host", "localhost");
        int port = env.getProperty("spring.data.redis.port", Integer.class, 6379);

        LettuceConnectionFactory factory = new LettuceConnectionFactory(host, port);
        factory.afterPropertiesSet();
        return factory;
    }

    @Primary
    @Bean("redisTemplate")
    public RedisTemplate<String, String> redisTemplate(
            @Qualifier("masterRedisConnectionFactory") RedisConnectionFactory cf
    ) {
        return createHashRedisTemplate(cf);
    }

    @Bean("masterHashRedisTemplate")
    public RedisTemplate<String, String> masterHashRedisTemplate(
            @Qualifier("masterRedisConnectionFactory") RedisConnectionFactory cf
    ) {
        return createHashRedisTemplate(cf);
    }

    @Bean
    public StringRedisHashOperations redisHashOperation(
            @Qualifier("masterHashRedisTemplate") RedisTemplate<String, String> redisTemplate
    ) {
        return new StringRedisHashOperations(redisTemplate);
    }

    @Bean("redisNotificationCodec")
    public RedisHashCodec<RedisNotification> redisNotificationCodec(ObjectMapper objectMapper) {
        return new RedisNotificationCodec(objectMapper);
    }

    @Bean("warmUpNotification_lua")
    public RedisScript<Boolean> warmUpNotification() {
        return RedisScript.of(new ClassPathResource("META-INF/scripts/warmUpNotification.lua"), Boolean.class);
    }

    @Bean("invalidateNotification_lua")
    public RedisScript<Boolean> invalidateNotification() {
        return RedisScript.of(new ClassPathResource("META-INF/scripts/invalidateNotification.lua"), Boolean.class);
    }

    @Bean
    public RedisNotificationAdapter redisNotificationAdapter(
            @Qualifier("masterHashRedisTemplate") RedisTemplate<String, String> masterHashRedisTemplate,
            StringRedisHashOperations redisHashOperation,
            @Qualifier("redisNotificationCodec") RedisHashCodec<RedisNotification> redisNotificationCodec,
            @Qualifier("warmUpNotification_lua") RedisScript<Boolean> warmUpNotification_lua,
            @Qualifier("invalidateNotification_lua") RedisScript<Boolean> invalidateNotification_lua
    ) {
        return new RedisNotificationAdapter(
                masterHashRedisTemplate,
                redisHashOperation,
                redisNotificationCodec,
                warmUpNotification_lua,
                invalidateNotification_lua
        );
    }

    private RedisTemplate<String, String> createHashRedisTemplate(RedisConnectionFactory cf) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(cf);

        StringRedisSerializer serializer = new StringRedisSerializer();

        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}
