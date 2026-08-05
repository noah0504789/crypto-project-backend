package org.example.chat.chatmessage.adapter.out.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.exception.ChatCacheException;
import org.example.common.time.Clock;
import org.example.chat.infra.properties.ChatCacheProperties;
import org.example.chat.infra.redis.RedisCollectionRegistry;
import org.example.common.redis.codec.RedisValueCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.example.common.enums.RedisKey.CHAT_MESSAGE_INFO;
import static org.example.common.enums.RedisKey.CHAT_MESSAGE_ACCESS_BY_ROOM_INDEX;

@Slf4j
@Component
public class ChatMessageScheduler {

    private final RedisCollectionRegistry registry;
    private final RedisTemplate<String, String> redisTemplate;
    private final Clock clockService;
    private final RedisValueCodec<ChatMessage> redisChatMessageCodec;
    private final ChatCacheProperties chatCacheProperties;

    public ChatMessageScheduler(
            RedisCollectionRegistry registry,
            RedisTemplate<String, String> redisTemplate,
            Clock clockService,
            @Qualifier("redisChatMessageCodec") RedisValueCodec<ChatMessage> redisChatMessageCodec,
            ChatCacheProperties chatCacheProperties) {
        this.registry = registry;
        this.redisTemplate = redisTemplate;
        this.clockService = clockService;
        this.redisChatMessageCodec = redisChatMessageCodec;
        this.chatCacheProperties = chatCacheProperties;
    }

    @Scheduled(cron = "${chat.scheduler.message-cleanup.cron:0 0 3 * * *}")
    public void removeExpiringMessages() {
        long ttlMillis = chatCacheProperties.messageRetention().toMillis();

        Instant now = clockService.now();
        long cutoff = now.toEpochMilli() - ttlMillis;

        String messageAccessKeyPattern = CHAT_MESSAGE_ACCESS_BY_ROOM_INDEX.keyFor("*");

        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions()
                        .match(messageAccessKeyPattern)
                        .count(100)
                        .build()
        )) {
            while (cursor.hasNext()) {
                String messageAccessKey = cursor.next();

                Set<String> expiredMsgIds = registry.getMasterZSet(messageAccessKey).rangeByScore(0, cutoff);

                if (expiredMsgIds.isEmpty()) {
                    continue;
                }

                String roomId = CHAT_MESSAGE_ACCESS_BY_ROOM_INDEX.extractIdentifier(messageAccessKey);
                String messageKey = CHAT_MESSAGE_INFO.keyFor(roomId);

                Set<String> cachedMessages = registry.getMasterZSet(messageKey).range(0, -1);

                if (cachedMessages.isEmpty()) {
                    expiredMsgIds.forEach(id -> registry.getMasterZSet(messageAccessKey).remove(id));
                    continue;
                }

                List<String> expiredMessageValues = cachedMessages.stream()
                        .filter(Objects::nonNull)
                        .filter(value -> expiredMsgIds.contains(redisChatMessageCodec.read(value).getId()))
                        .toList();

                if (!expiredMessageValues.isEmpty()) {
                    expiredMessageValues.forEach(value -> registry.getMasterZSet(messageKey).remove(value));
                }

                expiredMsgIds.forEach(id -> registry.getMasterZSet(messageAccessKey).remove(id));
            }
        } catch (Exception e) {
            throw new ChatCacheException("Failed to remove expiring messages from cache", e);
        }
    }
}