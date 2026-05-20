package org.example.chatmessage.adapter.out.cache;

import lombok.extern.slf4j.Slf4j;
import org.example.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chatmessage.domain.model.ChatMessage;
import org.example.chatroom.adapter.dto.MembershipScore;
import org.example.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chatroom.domain.model.ChatRoom;
import org.example.chatroom.domain.model.ChatRoomCategory;
import org.example.common.cache.CacheFailOpen;
import org.example.common.clock.Clock;
import org.example.common.exception.ChatRoomNotFoundException;
import org.example.infra.redis.RedisCollectionRegistry;
import org.example.common.redis.RedisValueCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.example.common.exception.ChatCacheException;
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
    private final Clock instantClock;

    public RedisChatMessageAdapter(
            @Qualifier("masterHashRedisTemplate") RedisTemplate<String, String> masterHashRedisTemplate,
            @Qualifier("replicaHashRedisTemplate") RedisTemplate<String, String> replicaHashRedisTemplate,
            RedisCollectionRegistry registry,
            @Qualifier("redisChatMessageCodec") RedisValueCodec<ChatMessage> redisChatMessageCodec,
            @Qualifier("storeChatMessage_lua") RedisScript<Boolean> storeChatMessage_lua,
            @Qualifier("warmUpChatMessageList_lua") RedisScript<Boolean> warmUpChatMessageList_lua,
            @Qualifier("deleteChatMessage_lua") RedisScript<Long> deleteChatMessage_lua,
            @Qualifier("instantClock") Clock instantClock
    ) {
        this.masterHashRedisTemplate = masterHashRedisTemplate;
        this.replicaHashRedisTemplate = replicaHashRedisTemplate;
        this.registry = registry;
        this.redisChatMessageCodec = redisChatMessageCodec;
        this.storeChatMessage_lua = storeChatMessage_lua;
        this.warmUpChatMessageList_lua = warmUpChatMessageList_lua;
        this.deleteChatMessage_lua = deleteChatMessage_lua;
        this.instantClock = instantClock;
    }

    @Override
    @CacheFailOpen
    public List<ChatMessage> listLatest(String roomId, int limit) {
        String messageKey = CHAT_MESSAGE.keyFor(roomId);

        return registry.getMasterZSet(messageKey).reverseRange(0, limit - 1).stream()
                .filter(Objects::nonNull)
                .map(redisChatMessageCodec::read)
                .peek(this::updateLastAccessedAt)
                .toList();
    }

    @Override
    @CacheFailOpen
    public List<ChatMessage> listPrev(String roomId, String lastId, Long lastCreatedAtMillis, int limit) {
        ZSetOperations<String, String> zOps = replicaHashRedisTemplate.opsForZSet();
        String messageKey = CHAT_MESSAGE.keyFor(roomId);
        int buffer = limit * 2;

        Set<String> messageIds = zOps.reverseRangeByScore(
                messageKey,
                Double.NEGATIVE_INFINITY,
                Math.nextDown(lastCreatedAtMillis.doubleValue()),
                0L,
                buffer
        );

        return messageIds.stream()
                .filter(Objects::nonNull)
                .map(redisChatMessageCodec::read)
                .filter(message -> {
                    long ts = message.toEpochMillis();
                    if (ts < lastCreatedAtMillis) return true;
                    if (ts > lastCreatedAtMillis) return false;
                    return message.getId().compareTo(lastId) < 0;
                })
                .limit(limit)
                .peek(this::updateLastAccessedAt)
                .toList();
    }

    @Override
    public void save(ChatMessage domain, ChatRoomCategory category, Set<String> memberIds_) {
        String id = domain.getId();
        String roomId = domain.getRoomId();
        Instant createdAt = domain.toInstant();
        long createdMs = domain.toEpochMillis();
        String content = domain.getContent();
        String writerId = domain.getWriterId();
        double scoreIncrement = 1.0;

        List<String> keys = new ArrayList<>();
        String messageKey = CHAT_MESSAGE.keyFor(roomId);
        String messageAccessKey = ACCESS_CHAT_MESSAGE_BY_ROOM.keyFor(roomId);
        String roomInfoKey = CHAT_ROOM_INFO.keyFor(roomId);
        String roomPopularKey = POPULAR_CHAT_ROOM_BY_CATEGORY_INDEX.keyFor(category.name());
        String writerRecentKey = RECENT_CHAT_ROOM_BY_MEMBER_INDEX.keyFor(writerId);

        Collections.addAll(keys, messageKey, messageAccessKey, roomInfoKey, roomPopularKey, writerRecentKey);

        Set<String> memberIds = new HashSet<>(memberIds_);
        memberIds.remove(writerId);
        memberIds.stream()
                .map(RECENT_CHAT_ROOM_BY_MEMBER_INDEX::keyFor)
                .forEach(keys::add);

        List<String> args = new ArrayList<>();
        args.add(id);
        args.add(roomId);
        args.add(createdAt.toString());
        args.add(String.valueOf(createdMs));
        args.add(String.valueOf(scoreIncrement));
        args.add(content);
        args.add(writerId);
        args.add(redisChatMessageCodec.write(domain));

        if (!masterHashRedisTemplate.execute(storeChatMessage_lua, keys, args.toArray())) {
            throw new ChatCacheException("[redis] chatmessage save failed!");
        }
    }

    @Override
    public void warmUpList(List<ChatMessage> list, String roomId) {
        String messageKey = CHAT_MESSAGE.keyFor(roomId);

        List<String> keys = List.of(messageKey);
        List<String> args = new ArrayList<>();
        args.add(list.size()+"");

        for (ChatMessage msg : list) {
            args.add(String.valueOf(msg.toEpochMillis()));
            args.add(redisChatMessageCodec.write(msg));
        }

        if (!masterHashRedisTemplate.execute(warmUpChatMessageList_lua, keys, args.toArray())) {
            throw new ChatCacheException("[redis] chatmessage warmUpMessages failed!");
        }
    }

    @Override
    public void hardDelete(String id, String roomId, List<MembershipScore> membershipScores) {
        List<MembershipScore> validScores = membershipScores == null
                ? List.of()
                : membershipScores.stream()
                    .filter(Objects::nonNull)
                    .filter(score -> score.memberId() != null)
                    .toList();

        List<String> keys = new ArrayList<>();
        String messageKey = CHAT_MESSAGE.keyFor(roomId);
        String messageAccessKey = ACCESS_CHAT_MESSAGE_BY_ROOM.keyFor(roomId);
        String roomInfoKey = CHAT_ROOM_INFO.keyFor(roomId);
        Collections.addAll(keys, messageKey, messageAccessKey, roomInfoKey);
        validScores.stream()
                .map(MembershipScore::memberId)
                .map(RECENT_CHAT_ROOM_BY_MEMBER_INDEX::keyFor)
                .forEach(keys::add);

        List<String> args = new ArrayList<>();
        args.add(id);
        args.add(roomId);
        args.add(validScores.size()+"");
        for (MembershipScore membershipScore : validScores) {
            Long score = membershipScore.score();
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
        String messageAccessKey = ACCESS_CHAT_MESSAGE_BY_ROOM.keyFor(message.getRoomId());
        long epochMilli = ((Instant) instantClock.now()).toEpochMilli();

        registry.getMasterZSet(messageAccessKey).add(message.getId(), epochMilli);
    }
}