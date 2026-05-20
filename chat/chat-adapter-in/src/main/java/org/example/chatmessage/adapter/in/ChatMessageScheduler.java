package org.example.chatmessage.adapter.in;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chatmessage.domain.model.ChatMessage;
import org.example.common.exception.ChatCacheException;
import org.example.common.clock.Clock;
import org.example.infra.redis.RedisCollectionRegistry;
import org.example.common.redis.RedisValueCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.example.common.enums.RedisKey.CHAT_MESSAGE;
import static org.example.common.enums.RedisKey.ACCESS_CHAT_MESSAGE_BY_ROOM;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageScheduler {

    private final RedisCollectionRegistry registry;
    private final RedisTemplate<String, String> redisTemplate;

    private final Clock clock;

    @Qualifier("redisChatMessageCodec")
    private final RedisValueCodec<ChatMessage> redisChatMessageCodec;

    @Scheduled(cron = "0 0 3 * * *")
    public void removeExpiringMessages() {
        long ttlMillis = Duration.ofDays(7).toMillis();

        Instant now = clock.now();
        long cutoff = now.toEpochMilli() - ttlMillis;

        String messageAccessKeyPattern = ACCESS_CHAT_MESSAGE_BY_ROOM.keyFor("*");

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

                String roomId = ACCESS_CHAT_MESSAGE_BY_ROOM.extractIdentifier(messageAccessKey);
                String messageKey = CHAT_MESSAGE.keyFor(roomId);

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