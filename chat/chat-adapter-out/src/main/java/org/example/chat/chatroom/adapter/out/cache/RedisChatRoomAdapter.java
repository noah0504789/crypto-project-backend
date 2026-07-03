package org.example.chat.chatroom.adapter.out.cache;

import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatroom.application.service.result.ChatRoomCacheLookupResult;
import org.example.chat.chatroom.application.port.out.ChatRoomCachePort;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.common.redis.failover.CacheFailOpen;
import org.example.chat.infra.redis.RedisCollectionRegistry;
import org.example.common.redis.codec.RedisHashCodec;
import org.example.common.redis.operation.StringRedisHashOperations;
import org.example.common.redis.codec.RedisValueCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.example.chat.exception.ChatCacheException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import static org.example.common.enums.RedisKey.*;

@Repository
public class RedisChatRoomAdapter implements ChatRoomCachePort {

    private static final String CHAT_ROOM_CACHE_TTL_SECONDS = String.valueOf(Duration.ofDays(7).toSeconds());
    private final RedisTemplate<String, String> masterHashRedisTemplate;
    private final RedisTemplate<String, String> replicaHashRedisTemplate;
    private final StringRedisHashOperations hash;
    private final RedisHashCodec<RedisChatRoom> redisChatRoomCodec;
    private final RedisValueCodec<ChatMessage> redisChatMessageCodec;
    private final RedisCollectionRegistry registry;
    private final RedisScript<Boolean> storeChatRoom_lua;
    private final RedisScript<Boolean> warmUpChatRoom_lua;
    private final RedisScript<Boolean> warmUpChatRoomList_lua;
    private final RedisScript<Boolean> updateChatRoom_lua;
    private final RedisScript<Boolean> joinChatRoom_lua;
    private final RedisScript<Boolean> leaveChatRoom_lua;
    private final RedisScript<Boolean> deleteChatRoom_lua;
    private final RedisScript<Boolean> recoverUpdateChatRoom_lua;
    private final RedisScript<Boolean> invalidateChatRoomActivity_lua;
    private final RedisScript<Boolean> invalidateChatRoomInfo_lua;

    public RedisChatRoomAdapter(
            @Qualifier("masterHashRedisTemplate") RedisTemplate<String, String> masterHashRedisTemplate,
            @Qualifier("replicaHashRedisTemplate") RedisTemplate<String, String> replicaHashRedisTemplate,
            StringRedisHashOperations hash,
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
        this.masterHashRedisTemplate = masterHashRedisTemplate;
        this.replicaHashRedisTemplate = replicaHashRedisTemplate;
        this.hash = hash;
        this.redisChatRoomCodec = redisChatRoomCodec;
        this.redisChatMessageCodec = redisChatMessageCodec;
        this.registry = registry;
        this.storeChatRoom_lua = storeChatRoom_lua;
        this.warmUpChatRoom_lua = warmUpChatRoom_lua;
        this.warmUpChatRoomList_lua = warmUpChatRoomList_lua;
        this.updateChatRoom_lua = updateChatRoom_lua;
        this.joinChatRoom_lua = joinChatRoom_lua;
        this.leaveChatRoom_lua = leaveChatRoom_lua;
        this.deleteChatRoom_lua = deleteChatRoom_lua;
        this.recoverUpdateChatRoom_lua = recoverUpdateChatRoom_lua;
        this.invalidateChatRoomActivity_lua = invalidateChatRoomActivity_lua;
        this.invalidateChatRoomInfo_lua = invalidateChatRoomInfo_lua;
    }

    @Override
    @CacheFailOpen
    public ChatRoomCacheLookupResult listPopularRooms(ChatRoomCategory category, int limit) {
        String popularKey = POPULAR_CHAT_ROOM_BY_CATEGORY_INDEX.keyFor(category.name());

        List<String> orderedIds = registry.getReplicaZSet(popularKey).reverseRange(0, limit - 1).stream()
                .filter(Objects::nonNull)
                .toList();

        return lookupByOrderedIds(orderedIds);
    }

    @Override
    @CacheFailOpen
    public ChatRoomCacheLookupResult listPopularRoomsAfter(ChatRoomCategory category, String lastRoomId, Long lastPopularity, int limit) {
        ZSetOperations<String, String> zOps = replicaHashRedisTemplate.opsForZSet();

        String popularKey = POPULAR_CHAT_ROOM_BY_CATEGORY_INDEX.keyFor(category.name());
        int buffer = limit * 2;

        Stream<String> s1 = zOps.reverseRangeByScore(
                popularKey,
                Double.NEGATIVE_INFINITY,
                Math.nextDown(lastPopularity.doubleValue()),
                0L,
                limit
        ).stream();

        Stream<String> s2 = zOps.reverseRangeByScoreWithScores(
                        popularKey,
                        lastPopularity.doubleValue(),
                        lastPopularity.doubleValue(),
                        0L,
                        buffer
                ).stream()
                .map(ZSetOperations.TypedTuple::getValue).filter(Objects::nonNull)
                .filter(roomId -> roomId.compareTo(lastRoomId) < 0);

        List<String> orderedIds = Stream.concat(s2, s1)
                .filter(Objects::nonNull)
                .limit(limit)
                .toList();

        return lookupByOrderedIds(orderedIds);
    }

    @Override
    @CacheFailOpen
    public ChatRoomCacheLookupResult listLatestActiveRooms(String memberId, int limit) {
        String recentRoomKey = RECENT_CHAT_ROOM_BY_MEMBER_INDEX.keyFor(memberId);

        List<String> orderedIds = registry.getMasterZSet(recentRoomKey).reverseRange(0, limit - 1).stream()
                .filter(Objects::nonNull)
                .toList();

        return lookupByOrderedIds(orderedIds);
    }

    @Override
    @CacheFailOpen
    public ChatRoomCacheLookupResult listActiveRoomsBefore(String memberId, String lastRoomId, Long lastScore, int limit) {
        ZSetOperations<String, String> zOps = masterHashRedisTemplate.opsForZSet();

        String recentRoomKey = RECENT_CHAT_ROOM_BY_MEMBER_INDEX.keyFor(memberId);
        int buffer = limit * 2;

        Stream<String> s1 = zOps.reverseRangeByScore(
                recentRoomKey,
                Double.NEGATIVE_INFINITY,
                Math.nextDown(lastScore.doubleValue()),
                0L,
                limit
        ).stream();

        Stream<String> s2 = zOps.reverseRangeByScoreWithScores(
                        recentRoomKey,
                        lastScore.doubleValue(),
                        lastScore.doubleValue(),
                        0L,
                        buffer
                ).stream()
                .map(ZSetOperations.TypedTuple::getValue).filter(Objects::nonNull)
                .filter(roomId -> roomId.compareTo(lastRoomId) < 0);

        List<String> orderedIds = Stream.concat(s2, s1)
                .filter(Objects::nonNull)
                .limit(limit)
                .toList();

        return lookupByOrderedIds(orderedIds);
    }

    @Override
    @CacheFailOpen
    public Optional<ChatRoom> findById(String id) {
        String infoKey = CHAT_ROOM_INFO.keyFor(id);

        return Optional.ofNullable(hash.find(infoKey))
                .filter(map -> !map.isEmpty())
                .map(redisChatRoomCodec::read)
                .map(redisRoom -> {
                    ChatMessage latest = findLastMessage(id).orElse(null);
                    return ChatRoom.rehydrateWithLatest(
                            redisRoom.getId(),
                            redisRoom.getHostId(),
                            redisRoom.getTitle(),
                            redisRoom.getDescription(),
                            redisRoom.getCategory(),
                            redisRoom.getMemberIds(),
                            redisRoom.getMsgCnt(),
                            latest,
                            redisRoom.toLocalDateTime()
                    );
                });
    }

    @Override
    @CacheFailOpen
    public Optional<Boolean> existsByTitle(String title) {
        String titleKey = CHAT_ROOM_TITLE_UNIQUE_INDEX.keyFor();
        return Optional.ofNullable(registry.getMasterSet(titleKey))
                .map(set -> set.contains(title));
    }

    @Override
    @CacheFailOpen
    public Optional<Long> getLastReadSeq(String roomId, String memberId) {
        String lastReadKey = CHAT_ROOM_LAST_READ.keyFor(roomId);
        String lastReadSeq = hash.findField(lastReadKey, memberId);

        return lastReadSeq == null || lastReadSeq.isBlank() ? Optional.empty() : Optional.of(Long.parseLong(lastReadSeq));
    }

    @Override
    public void save(ChatRoom domain) {
        String id = domain.getId();
        ChatRoomCategory category = domain.getCategory();

        String infoKey = CHAT_ROOM_INFO.keyFor(id);
        String titleKey = CHAT_ROOM_TITLE_UNIQUE_INDEX.keyFor();
        String topKey = POPULAR_CHAT_ROOM_BY_CATEGORY_INDEX.keyFor(category.name());
        String lastReadKey = CHAT_ROOM_LAST_READ.keyFor(id);
        List<String> keys = List.of(infoKey, titleKey, topKey, lastReadKey);

        List<String> args = new ArrayList<>();
        args.add(id);
        args.add(domain.getTitle());
        args.add("0.0");

        List<String> infoArgs = toRoomInfoArgs(domain);
        args.add(String.valueOf(infoArgs.size() / 2));
        args.addAll(infoArgs);

        args.add("0");
        args.add(String.valueOf(domain.getMemberIds().size()));
        args.addAll(domain.getMemberIds());
        args.add(CHAT_ROOM_CACHE_TTL_SECONDS);

        if (!masterHashRedisTemplate.execute(storeChatRoom_lua, keys, args.toArray())) {
            throw new ChatCacheException("[redis] chatroom save() failed!");
        }
    }

    @Override
    public void warmUp(ChatRoom domain) {
        String id = domain.getId();
        ChatRoomCategory category = domain.getCategory();

        String infoKey = CHAT_ROOM_INFO.keyFor(id);
        String titleKey = CHAT_ROOM_TITLE_UNIQUE_INDEX.keyFor();
        String topKey = POPULAR_CHAT_ROOM_BY_CATEGORY_INDEX.keyFor(category.name());
        List<String> keys = List.of(infoKey, titleKey, topKey);

        List<String> args = new ArrayList<>();
        args.add(id);
        args.add(domain.getTitle());
        args.add(String.valueOf(domain.getPopularity()));

        List<String> infoArgs = toRoomInfoArgs(domain);
        args.add(String.valueOf(infoArgs.size() / 2));
        args.addAll(infoArgs);
        args.add(CHAT_ROOM_CACHE_TTL_SECONDS);

        if (!masterHashRedisTemplate.execute(warmUpChatRoom_lua, keys, args.toArray())) {
            throw new ChatCacheException("[redis] chatroom warmUp() failed!");
        }
    }

    @Override
    public void warmUpList(List<ChatRoom> rooms, Map<String, Double> popularityScores) {
        List<String> keys = new ArrayList<>();
        List<String> args = new ArrayList<>();

        args.add(String.valueOf(rooms.size()));

        for (ChatRoom domain : rooms) {
            String id = domain.getId();
            ChatRoomCategory category = domain.getCategory();

            String infoKey = CHAT_ROOM_INFO.keyFor(id);
            String titleKey = CHAT_ROOM_TITLE_UNIQUE_INDEX.keyFor();
            String topKey = POPULAR_CHAT_ROOM_BY_CATEGORY_INDEX.keyFor(category.name());
            Collections.addAll(keys, infoKey, titleKey, topKey);

            args.add(id);
            args.add(domain.getTitle());
            args.add(String.valueOf(popularityScores.getOrDefault(id, 0.0)));

            List<String> infoArgs = toRoomInfoArgs(domain);
            args.add(String.valueOf(infoArgs.size() / 2));
            args.addAll(infoArgs);
            args.add(CHAT_ROOM_CACHE_TTL_SECONDS);
        }

        if (!masterHashRedisTemplate.execute(warmUpChatRoomList_lua, keys, args.toArray())) {
            throw new ChatCacheException("[redis] chatroom warmUpList() failed!");
        }
    }

    @Override
    public void updateRoom(String id, Map<String, Object> updates, String oldTitle) {
        String infoKey = CHAT_ROOM_INFO.keyFor(id);

        Map<String, String> encoded = redisChatRoomCodec.writePartial(updates);

        if (!updates.containsKey("title")) {
            hash.update(infoKey, encoded);
            return;
        }

        String titleKey = CHAT_ROOM_TITLE_UNIQUE_INDEX.keyFor();
        String newTitle = encoded.get("title");

        List<String> keys = List.of(infoKey, titleKey);
        List<String> args = new ArrayList<>();
        args.add(oldTitle == null ? "" : oldTitle);
        args.add(newTitle == null ? "" : newTitle);

        List<String> updatedArgs = new ArrayList<>();
        encoded.forEach((k, v) -> {
            updatedArgs.add(k);
            updatedArgs.add(v == null ? "" : v);
        });

        args.add(String.valueOf(updatedArgs.size() / 2));
        args.addAll(updatedArgs);
        args.add(CHAT_ROOM_CACHE_TTL_SECONDS);

        if (!masterHashRedisTemplate.execute(updateChatRoom_lua, keys, args.toArray())) throw new ChatCacheException("[redis] chatroom update() failed!");
    }

    @Override
    public void joinMembership(String id, String memberId) {
        String infoKey = CHAT_ROOM_INFO.keyFor(id);

        List<String> keys = List.of(infoKey);
        List<String> args = List.of(memberId);

        if (!masterHashRedisTemplate.execute(joinChatRoom_lua, keys, args.toArray())) {
            throw new ChatCacheException("[redis] chatroom join() failed!");
        }
    }

    @Override
    public boolean leaveMembership(String id, String memberId) {
        String infoKey = CHAT_ROOM_INFO.keyFor(id);
        String lastReadKey = CHAT_ROOM_LAST_READ.keyFor(id);
        String recentRoomKey = RECENT_CHAT_ROOM_BY_MEMBER_INDEX.keyFor(memberId);

        List<String> keys = List.of(infoKey, lastReadKey, recentRoomKey);
        List<String> args = List.of(id, memberId);

        return masterHashRedisTemplate.execute(leaveChatRoom_lua, keys, args.toArray());
    }

    @Override
    public void deleteRoom(String id, ChatRoomCategory category, String title, Set<String> memberIds) {
        List<String> keys = new ArrayList<>();

        String messageKey = CHAT_MESSAGE.keyFor(id);
        String messageAccessKey = ACCESS_CHAT_MESSAGE_BY_ROOM.keyFor(id);
        String roomInfoKey = CHAT_ROOM_INFO.keyFor(id);
        String roomLastReadKey = CHAT_ROOM_LAST_READ.keyFor(id);
        String roomTopKey = POPULAR_CHAT_ROOM_BY_CATEGORY_INDEX.keyFor(category.name());
        String roomTitleKey = CHAT_ROOM_TITLE_UNIQUE_INDEX.keyFor();
        Collections.addAll(keys, messageKey, messageAccessKey, roomInfoKey, roomLastReadKey, roomTopKey, roomTitleKey);

        memberIds.stream()
                .map(RECENT_CHAT_ROOM_BY_MEMBER_INDEX::keyFor)
                .forEach(keys::add);

        if (!masterHashRedisTemplate.execute(deleteChatRoom_lua, keys, id, title)) {
            throw new ChatCacheException("[redis] chatroom delete() failed!");
        }
    }

    @Override
    public void updateLastReadSeq(String id, String memberId, Long lastReadSeq) {
        String lastReadKey = CHAT_ROOM_LAST_READ.keyFor(id);
        hash.update(lastReadKey, memberId, String.valueOf(lastReadSeq));
    }

    @Override
    public void updateActivityScore(String id, String memberId, Long score) {
        String recentRoomKey = RECENT_CHAT_ROOM_BY_MEMBER_INDEX.keyFor(memberId);
        registry.getMasterZSet(recentRoomKey).add(id, score);
    }

    @Override
    public void recoverRoomUpdate(ChatRoom chatRoom, String oldTitle) {
        String id = chatRoom.getId();
        ChatRoomCategory category = chatRoom.getCategory();

        String infoKey = CHAT_ROOM_INFO.keyFor(id);
        String titleKey = CHAT_ROOM_TITLE_UNIQUE_INDEX.keyFor();
        String topKey = POPULAR_CHAT_ROOM_BY_CATEGORY_INDEX.keyFor(category.name());
        List<String> keys = List.of(infoKey, titleKey, topKey);

        List<String> args = new ArrayList<>();
        args.add(id);
        args.add(oldTitle == null ? "" : oldTitle);
        args.add(chatRoom.getTitle());
        args.add(chatRoom.getPopularity()+"");

        List<String> infoArgs = toRoomInfoArgs(chatRoom);
        args.add(String.valueOf(infoArgs.size() / 2));
        args.addAll(infoArgs);
        args.add(CHAT_ROOM_CACHE_TTL_SECONDS);

        if (!masterHashRedisTemplate.execute(recoverUpdateChatRoom_lua, keys, args.toArray())) {
            throw new ChatCacheException("[redis] chatroom recoverUpdate() failed!");
        }
    }

    @Override
    public void invalidateMembershipActivity(String id, String memberId) {
        String lastReadKey = CHAT_ROOM_LAST_READ.keyFor(id);
        String recentRoomKey = RECENT_CHAT_ROOM_BY_MEMBER_INDEX.keyFor(memberId);

        List<String> keys = List.of(lastReadKey, recentRoomKey);
        List<String> args = List.of(id, memberId);

        if (!masterHashRedisTemplate.execute(invalidateChatRoomActivity_lua, keys, args.toArray())) {
            throw new ChatCacheException("[redis] chatroom invalidateActivity() failed!");
        }
    }

    @Override
    public void invalidateRoomInfo(String id) {
        String infoKey = CHAT_ROOM_INFO.keyFor(id);
        List<String> keys = List.of(infoKey);
        List<String> args = List.of(id);

        if (!masterHashRedisTemplate.execute(invalidateChatRoomInfo_lua, keys, args.toArray())) {
            throw new ChatCacheException("[redis] chatroom invalidateInfo() failed!");
        }
    }

    @CacheFailOpen
    private Optional<ChatMessage> findLastMessage(String roomId) {
        String infoKey = CHAT_MESSAGE.keyFor(roomId);

        return registry.getMasterZSet(infoKey)
                .reverseRange(0, 0)
                .stream()
                .findFirst()
                .map(redisChatMessageCodec::read);
    }

    private ChatRoomCacheLookupResult lookupByOrderedIds(List<String> orderedIds) {
        List<ChatRoom> hits = new ArrayList<>();
        List<String> misses = new ArrayList<>();

        for (String roomId : orderedIds) {
            Optional<ChatRoom> chatRoom = findById(roomId);

            if (chatRoom.isPresent()) {
                hits.add(chatRoom.get());
            } else {
                misses.add(roomId);
            }
        }

        return new ChatRoomCacheLookupResult(
                orderedIds,
                hits,
                misses
        );
    }

    private List<String> toRoomInfoArgs(ChatRoom domain) {
        RedisChatRoom entity = RedisChatRoom.fromDomain(domain);
        Map<String, String> entityMap = redisChatRoomCodec.write(entity);

        List<String> args = new ArrayList<>(entityMap.size() * 2);
        entityMap.forEach((k, v) -> {
            args.add(k);
            args.add(v == null ? "" : v);
        });
        return args;
    }
}
