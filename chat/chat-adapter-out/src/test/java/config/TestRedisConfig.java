package config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.example.chatmessage.adapter.out.cache.RedisChatMessageAdapter;
import org.example.chatmessage.adapter.out.cache.RedisChatMessageCodec;
import org.example.chatmessage.domain.model.ChatMessage;
import org.example.chatroom.adapter.out.cache.RedisChatRoom;
import org.example.chatroom.adapter.out.cache.RedisChatRoomAdapter;
import org.example.chatroom.adapter.out.cache.RedisChatRoomCodec;
import org.example.common.clock.Clock;
import org.example.common.clock.ClockService;
import org.example.infra.redis.RedisCollectionRegistry;
import org.example.common.redis.codec.RedisHashCodec;
import org.example.common.redis.operation.StringRedisHashOperations;
import org.example.common.redis.codec.RedisValueCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.support.collections.RedisCollection;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestRedisConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY); // 빈 값 + NULL 인 필드를 JSON 출력에서 제외
        mapper.setDateFormat(new SimpleDateFormat("yyyy/MM/dd")); // 날짜(Date, Calendar) -> 문자열 포맷

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS");
        JavaTimeModule module = new JavaTimeModule();
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter)); // 날짜(LocalDate, LocalDateTime) -> 문자열 포맷
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(formatter)); // 날짜(LocalDate, LocalDateTime) -> 문자열 포맷
        mapper.registerModule(module);

        return mapper;
    }

    @Bean("instantClock")
    public Clock instantClock() {
        return new ClockService();
    }

    @Primary
    @Bean("masterRedisConnectionFactory")
    public RedisConnectionFactory masterRedisConnectionFactory(Environment env) {
        String host = env.getProperty("spring.data.redis.host", "localhost");
        int port = env.getProperty("spring.data.redis.port", Integer.class, 6379);

        System.out.println("MASTER TEST REDIS = " + host + ":" + port);

        LettuceConnectionFactory factory = new LettuceConnectionFactory(host, port);
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean("replicaRedisConnectionFactory")
    public RedisConnectionFactory replicaRedisConnectionFactory(Environment env) {
        String host = env.getProperty("spring.data.redis.host", "localhost");
        int port = env.getProperty("spring.data.redis.port", Integer.class, 6379);

        System.out.println("REPLICA TEST REDIS = " + host + ":" + port);

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

    @Bean("replicaHashRedisTemplate")
    public RedisTemplate<String, String> replicaHashRedisTemplate(
            @Qualifier("replicaRedisConnectionFactory") RedisConnectionFactory cf
    ) {
        return createHashRedisTemplate(cf);
    }

    @Bean
    public Cache<String, RedisCollection<String>> redisCollectionCache() {
        return Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterAccess(Duration.ofDays(3))
                .build();
    }

    @Bean
    public StringRedisHashOperations redisHashOperation(
            @Qualifier("masterHashRedisTemplate") RedisTemplate<String, String> redisTemplate
    ) {
        return new StringRedisHashOperations(redisTemplate);
    }

    @Bean
    public RedisCollectionRegistry redisCollectionRegistry(
            @Qualifier("masterHashRedisTemplate") RedisTemplate<String, String> masterRedisTemplate,
            @Qualifier("replicaHashRedisTemplate") RedisTemplate<String, String> replicaRedisTemplate,
            Cache<String, RedisCollection<String>> cache
    ) {
        return new RedisCollectionRegistry(
                masterRedisTemplate,
                replicaRedisTemplate,
                cache
        );
    }

    @Bean("redisChatRoomCodec")
    public RedisHashCodec<RedisChatRoom> redisChatRoomCodec(ObjectMapper objectMapper) {
        return new RedisChatRoomCodec(objectMapper);
    }

    @Bean("redisChatMessageCodec")
    public RedisValueCodec<ChatMessage> redisChatMessageCodec(ObjectMapper objectMapper) {
        return new RedisChatMessageCodec(objectMapper);
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

    @Bean
    public RedisChatRoomAdapter redisChatRoomAdapter(
            @Qualifier("masterHashRedisTemplate") RedisTemplate<String, String> masterHashRedisTemplate,
            @Qualifier("replicaHashRedisTemplate") RedisTemplate<String, String> replicaHashRedisTemplate,
            StringRedisHashOperations redisHashOperation,
            @Qualifier("redisChatRoomCodec") RedisHashCodec<RedisChatRoom> redisChatRoomCodec,
            @Qualifier("redisChatMessageCodec") RedisValueCodec<ChatMessage> redisChatMessageCodec,
            RedisCollectionRegistry registry,
            @Qualifier("storeChatRoom_lua") RedisScript<Boolean> storeChatRoom_lua,
            @Qualifier("warmUpChatRoom_lua") RedisScript<Boolean> warmUpChatRoom_lua,
            @Qualifier("warmUpChatRoomList_lua") RedisScript<Boolean> warmUpChatRoomList_lua,
            @Qualifier("updateChatRoom_lua") RedisScript<Boolean> updateChatRoom_lua,
            @Qualifier("joinChatRoom_lua") RedisScript<Boolean> joinChatRoom_lua,
            @Qualifier("leaveChatRoom_lua") RedisScript<Boolean> leaveChatRoom_lua,
            @Qualifier("deleteChatRoom_lua") RedisScript<Boolean> deleteChatRoom_lua,
            @Qualifier("recoverUpdateChatRoom_lua") RedisScript<Boolean> recoverUpdateChatRoom_lua,
            @Qualifier("invalidateChatRoomActivity_lua") RedisScript<Boolean> invalidateChatRoomActivity_lua,
            @Qualifier("invalidateChatRoomInfo_lua") RedisScript<Boolean> invalidateChatRoomInfo_lua
    ) {
        return new RedisChatRoomAdapter(
                masterHashRedisTemplate,
                replicaHashRedisTemplate,
                redisHashOperation,
                redisChatRoomCodec,
                redisChatMessageCodec,
                registry,
                storeChatRoom_lua,
                warmUpChatRoom_lua,
                warmUpChatRoomList_lua,
                updateChatRoom_lua,
                joinChatRoom_lua,
                leaveChatRoom_lua,
                deleteChatRoom_lua,
                recoverUpdateChatRoom_lua,
                invalidateChatRoomActivity_lua,
                invalidateChatRoomInfo_lua
        );
    }

    @Bean
    public RedisChatMessageAdapter redisChatMessageAdapter(
            @Qualifier("masterHashRedisTemplate") RedisTemplate<String, String> masterHashRedisTemplate,
            @Qualifier("replicaHashRedisTemplate") RedisTemplate<String, String> replicaHashRedisTemplate,
            RedisCollectionRegistry registry,
            @Qualifier("redisChatMessageCodec") RedisValueCodec<ChatMessage> redisChatMessageCodec,
            @Qualifier("storeChatMessage_lua") RedisScript<Boolean> storeChatMessage_lua,
            @Qualifier("warmUpChatMessageList_lua") RedisScript<Boolean> warmUpChatMessageList_lua,
            @Qualifier("deleteChatMessage_lua") RedisScript<Long> deleteChatMessage_lua,
            @Qualifier("instantClock") Clock clock
    ) {
        return new RedisChatMessageAdapter(
                masterHashRedisTemplate,
                replicaHashRedisTemplate,
                registry,
                redisChatMessageCodec,
                storeChatMessage_lua,
                warmUpChatMessageList_lua,
                deleteChatMessage_lua,
                clock
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
