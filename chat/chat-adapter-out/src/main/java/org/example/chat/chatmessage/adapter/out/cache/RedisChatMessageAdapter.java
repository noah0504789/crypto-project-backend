package org.example.chat.chatmessage.adapter.out.cache;

import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatroom.application.service.result.ChatRoomMembershipScore;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.common.redis.failover.CacheFailOpen;
import org.example.common.clock.Clock;
import org.example.chat.infra.redis.RedisCollectionRegistry;
import org.example.common.redis.codec.RedisValueCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.example.chat.exception.ChatCacheException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;

import static org.example.common.enums.RedisKey.*;

@Slf4j
@Repository
public class RedisChatMessageAdapter implements ChatMessageCachePort {

    private final RedisTemplate<String, String> masterHashRedisTemplate;
    private final RedisTemplate<String, String> replicaHashRedisTemplate;
    private final RedisCollectionRegistry registry;
    private final RedisValueCodec<ChatMessage> redisChatMessageCodec;
    private final RedisScript<Boolean> storeChatMessage_lua;
    private final RedisScript<Boolean> warmUpChatMessageList_lua;
    private final RedisScript<Long> deleteChatMessage_lua;
    private final Clock clock;

    public RedisChatMessageAdapter(
            @Qualifier("masterHashRedisTemplate") RedisTemplate<String, String> masterHashRedisTemplate,
            @Qualifier("replicaHashRedisTemplate") RedisTemplate<String, String> replicaHashRedisTemplate,
            RedisCollectionRegistry registry,
            @Qualifier("redisChatMessageCodec") RedisValueCodec<ChatMessage> redisChatMessageCodec,
            @Qualifier("storeChatMessage_lua") RedisScript<Boolean> storeChatMessage_lua,
            @Qualifier("warmUpChatMessageList_lua") RedisScript<Boolean> warmUpChatMessageList_lua,
            @Qualifier("deleteChatMessage_lua") RedisScript<Long> deleteChatMessage_lua,
            Clock clock
    ) {
        this.masterHashRedisTemplate = masterHashRedisTemplate;
        this.replicaHashRedisTemplate = replicaHashRedisTemplate;
        this.registry = registry;
        this.redisChatMessageCodec = redisChatMessageCodec;
        this.storeChatMessage_lua = storeChatMessage_lua;
        this.warmUpChatMessageList_lua = warmUpChatMessageList_lua;
        this.deleteChatMessage_lua = deleteChatMessage_lua;
        this.clock = clock;
    }

    @Override
    @CacheFailOpen
    public List<ChatMessage> listLatestMessages(String roomId, int limit) {
        String messageKey = CHAT_MESSAGE_INFO.keyFor(roomId);

        return registry.getMasterZSet(messageKey).reverseRange(0, limit - 1).stream()
                .filter(Objects::nonNull)
                .map(redisChatMessageCodec::read)
                .peek(this::updateLastAccessedAt)
                .toList();
    }

    @Override
    @CacheFailOpen
    public List<ChatMessage> listMessagesBefore(String roomId, String lastMsgId, Long lastCreatedAtMs, int limit) {
        ZSetOperations<String, String> zOps = replicaHashRedisTemplate.opsForZSet();
        String messageKey = CHAT_MESSAGE_INFO.keyFor(roomId);
        int buffer = limit * 2;

        Set<String> messageIds = zOps.reverseRangeByScore(
                messageKey,
                Double.NEGATIVE_INFINITY,
                Math.nextDown(lastCreatedAtMs.doubleValue()),
                0L,
                buffer
        );

        return messageIds.stream()
                .filter(Objects::nonNull)
                .map(redisChatMessageCodec::read)
                .filter(message -> {
                    long ts = message.toEpochMillis();
                    if (ts < lastCreatedAtMs) return true;
                    if (ts > lastCreatedAtMs) return false;
                    return message.getId().compareTo(lastMsgId) < 0;
                })
                .limit(limit)
                .peek(this::updateLastAccessedAt)
                .toList();
    }

    @Override
    public void save(ChatMessage message, ChatRoomCategory category, Set<String> memberIds_) {
        String id = message.getId();
        String roomId = message.getRoomId();
        Instant createdAt = message.createdAtInstant();
        long createdMs = message.toEpochMillis();
        String content = message.getContent();
        String writerId = message.getWriterId();
        double scoreIncrement = 1.0;

        List<String> keys = new ArrayList<>();
        String messageKey = CHAT_MESSAGE_INFO.keyFor(roomId);
        String messageAccessKey = CHAT_MESSAGE_ACCESS_BY_ROOM_INDEX.keyFor(roomId);
        String roomInfoKey = CHAT_ROOM_INFO.keyFor(roomId);
        String roomPopularKey = CHAT_ROOM_POPULAR_BY_CATEGORY_INDEX.keyFor(category.name());
        String writerRecentKey = CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX.keyFor(writerId);

        Collections.addAll(keys, messageKey, messageAccessKey, roomInfoKey, roomPopularKey, writerRecentKey);

        Set<String> memberIds = new HashSet<>(memberIds_);
        memberIds.remove(writerId);
        memberIds.stream()
                .map(CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX::keyFor)
                .forEach(keys::add);

        List<String> args = new ArrayList<>();
        args.add(id);
        args.add(roomId);
        args.add(createdAt.toString());
        args.add(String.valueOf(createdMs));
        args.add(String.valueOf(scoreIncrement));
        args.add(content);
        args.add(writerId);
        args.add(redisChatMessageCodec.write(message));

        if (!masterHashRedisTemplate.execute(storeChatMessage_lua, keys, args.toArray())) {
            throw new ChatCacheException("[redis] chatmessage save failed!");
        }
    }

    @Override
    public void warmUpList(List<ChatMessage> messages, String roomId) {
        String messageKey = CHAT_MESSAGE_INFO.keyFor(roomId);

        List<String> keys = List.of(messageKey);
        List<String> args = new ArrayList<>();
        args.add(messages.size()+"");

        for (ChatMessage msg : messages) {
            args.add(String.valueOf(msg.toEpochMillis()));
            args.add(redisChatMessageCodec.write(msg));
        }

        if (!masterHashRedisTemplate.execute(warmUpChatMessageList_lua, keys, args.toArray())) {
            throw new ChatCacheException("[redis] chatmessage warmUpMessages failed!");
        }
    }

    @Override
    public void hardDelete(String id, String roomId, List<ChatRoomMembershipScore> membershipScores) {
        List<ChatRoomMembershipScore> validScores = membershipScores == null
                ? List.of()
                : membershipScores.stream()
                    .filter(Objects::nonNull)
                    .filter(score -> score.memberId() != null)
                    .toList();

        List<String> keys = new ArrayList<>();
        String messageKey = CHAT_MESSAGE_INFO.keyFor(roomId);
        String messageAccessKey = CHAT_MESSAGE_ACCESS_BY_ROOM_INDEX.keyFor(roomId);
        String roomInfoKey = CHAT_ROOM_INFO.keyFor(roomId);
        Collections.addAll(keys, messageKey, messageAccessKey, roomInfoKey);
        validScores.stream()
                .map(ChatRoomMembershipScore::memberId)
                .map(CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX::keyFor)
                .forEach(keys::add);

        List<String> args = new ArrayList<>();
        args.add(id);
        args.add(roomId);
        args.add(validScores.size()+"");
        for (ChatRoomMembershipScore chatRoomMembershipScore : validScores) {
            Long score = chatRoomMembershipScore.score();
            args.add(String.valueOf(score == null ? 0L : score));
        }

        Long result = masterHashRedisTemplate.execute(deleteChatMessage_lua, keys, args.toArray());

        if (result == 1L) {
            log.info("[redis] chatmessage delete applied. messageId={}, roomId={}", id, roomId);
            return;
        }

        if (result == 2L) {
            log.warn("[redis] chatmessage delete skipped. not found in latest 200. messageId={}, roomId={}", id, roomId);
            return;
        }

        throw new ChatCacheException("[redis] chatmessage delete failed! unknown result=" + result);
    }

    private void updateLastAccessedAt(ChatMessage message) {
        String messageAccessKey = CHAT_MESSAGE_ACCESS_BY_ROOM_INDEX.keyFor(message.getRoomId());
        long nowMs = clock.nowMs();

        registry.getMasterZSet(messageAccessKey).add(message.getId(), nowMs);
    }
}
