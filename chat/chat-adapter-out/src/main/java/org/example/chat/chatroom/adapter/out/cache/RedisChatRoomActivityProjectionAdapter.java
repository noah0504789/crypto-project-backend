package org.example.chat.chatroom.adapter.out.cache;

import org.example.chat.chatroom.application.port.out.ChatRoomActivityProjectionPort;
import org.example.chat.chatroom.application.service.result.ChatRoomActivityClaim;
import org.example.chat.chatroom.application.service.result.ChatRoomActivityProjectionResult;
import org.example.chat.chatroom.application.service.result.ChatRoomMemberActivity;
import org.example.chat.chatroom.domain.service.MyChatRoomScoreCalculator;
import org.example.chat.exception.ChatCacheException;
import org.example.chat.infra.properties.ChatCacheProperties;
import org.example.chat.infra.redis.RedisCollectionRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

import static org.example.common.enums.RedisKey.CHAT_MESSAGE_INFO;
import static org.example.common.enums.RedisKey.CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX;
import static org.example.common.enums.RedisKey.CHAT_ROOM_ACTIVITY_RECENT_INDEX;
import static org.example.common.enums.RedisKey.CHAT_ROOM_ACTIVITY_INFLIGHT_INDEX;
import static org.example.common.enums.RedisKey.CHAT_ROOM_INFO;
import static org.example.common.enums.RedisKey.CHAT_ROOM_LAST_READ_SEQ;

/**
 * 방 activity projection 의 Redis 구현. dirty/inflight 인덱스는 모두 {@code {chat}} 해시태그라
 * 같은 슬롯에 있고, 멤버별 active-room key 는 Lua 안에서 조립한다 — 멤버 수만큼 KEYS 를 실어
 * 보내지 않기 위해서이며, 키 패턴 정의처는 여전히 {@code RedisKey} 하나다.
 */
@Repository
public class RedisChatRoomActivityProjectionAdapter implements ChatRoomActivityProjectionPort {

    private final RedisTemplate<String, String> masterHashRedisTemplate;
    private final RedisCollectionRegistry registry;
    private final String chatRoomCacheTtlSeconds;
    private final RedisScript<List> claimDirtyChatRooms_lua;
    private final RedisScript<List> projectChatRoomActivity_lua;
    private final RedisScript<List> reclaimStalledChatRooms_lua;
    private final RedisScript<Long> rebuildChatRoomActivity_lua;
    private final RedisScript<Boolean> requeueDirtyChatRoom_lua;

    public RedisChatRoomActivityProjectionAdapter(
            @Qualifier("masterHashRedisTemplate") RedisTemplate<String, String> masterHashRedisTemplate,
            RedisCollectionRegistry registry,
            ChatCacheProperties chatCacheProperties,
            @Qualifier("claimDirtyChatRooms_lua") RedisScript<List> claimDirtyChatRooms_lua,
            @Qualifier("projectChatRoomActivity_lua") RedisScript<List> projectChatRoomActivity_lua,
            @Qualifier("reclaimStalledChatRooms_lua") RedisScript<List> reclaimStalledChatRooms_lua,
            @Qualifier("rebuildChatRoomActivity_lua") RedisScript<Long> rebuildChatRoomActivity_lua,
            @Qualifier("requeueDirtyChatRoom_lua") RedisScript<Boolean> requeueDirtyChatRoom_lua
    ) {
        this.masterHashRedisTemplate = masterHashRedisTemplate;
        this.registry = registry;
        this.chatRoomCacheTtlSeconds = chatCacheProperties.roomTtlSeconds();
        this.claimDirtyChatRooms_lua = claimDirtyChatRooms_lua;
        this.projectChatRoomActivity_lua = projectChatRoomActivity_lua;
        this.reclaimStalledChatRooms_lua = reclaimStalledChatRooms_lua;
        this.rebuildChatRoomActivity_lua = rebuildChatRoomActivity_lua;
        this.requeueDirtyChatRoom_lua = requeueDirtyChatRoom_lua;
    }

    @Override
    public List<ChatRoomActivityClaim> claimDirtyRooms(int batchSize, long nowMs) {
        List<String> keys = List.of(dirtyKey(), inflightKey());
        Object[] args = {String.valueOf(batchSize), String.valueOf(nowMs)};

        List<?> claimed = masterHashRedisTemplate.execute(claimDirtyChatRooms_lua, keys, args);

        if (claimed == null || claimed.isEmpty()) {
            return List.of();
        }

        List<ChatRoomActivityClaim> claims = new ArrayList<>(claimed.size() / 2);

        for (int i = 0; i + 1 < claimed.size(); i += 2) {
            String roomId = String.valueOf(claimed.get(i));
            long activityMs = parseScore(String.valueOf(claimed.get(i + 1)));

            claims.add(new ChatRoomActivityClaim(roomId, activityMs));
        }

        return claims;
    }

    @Override
    public ChatRoomActivityProjectionResult project(String roomId, long claimedActivityMs) {
        List<String> keys = List.of(
                CHAT_ROOM_INFO.keyFor(roomId),
                CHAT_ROOM_LAST_READ_SEQ.keyFor(roomId),
                CHAT_MESSAGE_INFO.keyFor(roomId),
                inflightKey()
        );

        Object[] args = {
                roomId,
                CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX.identifierPrefix(),
                CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX.identifierSuffix(),
                String.valueOf(MyChatRoomScoreCalculator.UNREAD_PRIORITY_WEIGHT),
                String.valueOf(claimedActivityMs)
        };

        List<?> projected = masterHashRedisTemplate.execute(projectChatRoomActivity_lua, keys, args);

        if (projected == null || projected.size() < 2) {
            throw new ChatCacheException("[redis] chatroom activity projection returned no result. roomId=" + roomId);
        }

        int updatedMembers = Integer.parseInt(String.valueOf(projected.get(0)));

        if (updatedMembers < 0) {
            return ChatRoomActivityProjectionResult.ofCacheMiss();
        }

        return ChatRoomActivityProjectionResult.of(
                updatedMembers,
                Integer.parseInt(String.valueOf(projected.get(1)))
        );
    }

    @Override
    public List<String> reclaimStalledRooms(long staleBeforeMs, int batchSize, long nowMs) {
        List<String> keys = List.of(inflightKey());
        Object[] args = {String.valueOf(staleBeforeMs), String.valueOf(batchSize), String.valueOf(nowMs)};

        List<?> stalled = masterHashRedisTemplate.execute(reclaimStalledChatRooms_lua, keys, args);

        if (stalled == null || stalled.isEmpty()) {
            return List.of();
        }

        return stalled.stream()
                .map(String::valueOf)
                .toList();
    }

    @Override
    public void rebuild(String roomId, List<ChatRoomMemberActivity> memberActivities) {
        List<String> keys = List.of(CHAT_ROOM_LAST_READ_SEQ.keyFor(roomId), inflightKey());

        List<String> args = new ArrayList<>();
        args.add(roomId);
        args.add(CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX.identifierPrefix());
        args.add(CHAT_ROOM_ACTIVE_BY_MEMBER_INDEX.identifierSuffix());
        args.add(chatRoomCacheTtlSeconds);
        args.add(String.valueOf(memberActivities.size()));

        for (ChatRoomMemberActivity memberActivity : memberActivities) {
            args.add(memberActivity.memberId());
            args.add(String.valueOf(memberActivity.lastMsgReadSeq()));
            args.add(String.valueOf(memberActivity.score()));
        }

        Long rebuilt = masterHashRedisTemplate.execute(rebuildChatRoomActivity_lua, keys, args.toArray());

        if (rebuilt == null || rebuilt < 0) {
            throw new ChatCacheException("[redis] chatroom activity rebuild failed. roomId=" + roomId);
        }
    }

    @Override
    public void requeueDirty(String roomId, long activityMs) {
        List<String> keys = List.of(dirtyKey(), inflightKey());
        Object[] args = {roomId, String.valueOf(activityMs)};

        if (!Boolean.TRUE.equals(masterHashRedisTemplate.execute(requeueDirtyChatRoom_lua, keys, args))) {
            throw new ChatCacheException("[redis] chatroom activity requeue failed. roomId=" + roomId);
        }
    }

    @Override
    public void discard(String roomId) {
        registry.getMasterZSet(inflightKey()).remove(roomId);
        registry.getMasterZSet(dirtyKey()).remove(roomId);
    }

    @Override
    public long countDirtyRooms() {
        return registry.getMasterZSet(dirtyKey()).size();
    }

    private String dirtyKey() {
        return CHAT_ROOM_ACTIVITY_RECENT_INDEX.keyFor();
    }

    private String inflightKey() {
        return CHAT_ROOM_ACTIVITY_INFLIGHT_INDEX.keyFor();
    }

    private long parseScore(String score) {
        return (long) Double.parseDouble(score);
    }
}
