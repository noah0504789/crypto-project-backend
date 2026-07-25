package org.example.chat.chatroom.application.port.out;

import org.example.chat.chatroom.application.service.result.ChatRoomCacheLookupResult;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ChatRoomCachePort {

    void save(ChatRoom room);

    void warmUp(ChatRoom room);

    void warmUpList(List<ChatRoom> rooms);

    void updateRoom(
            String id,
            Map<String, Object> updates,
            String oldTitle
    );

    void joinMembership(String id, String memberId);

    boolean leaveMembership(String id, String memberId);

    void deleteRoom(
            String id,
            ChatRoomCategory category,
            String title,
            Set<String> memberIds
    );

    Optional<Long> getLastReadSeq(String roomId, String memberId);

    Optional<ChatRoom> findById(String id);

    Optional<Boolean> existsByTitle(String title);

    ChatRoomCacheLookupResult listPopularRooms(ChatRoomCategory category, int limit);

    ChatRoomCacheLookupResult listPopularRoomsAfter(
            ChatRoomCategory category,
            String lastRoomId,
            Long lastPopularity,
            int limit
    );

    ChatRoomCacheLookupResult listLatestActiveRooms(String memberId, int limit);

    ChatRoomCacheLookupResult listActiveRoomsBefore(
            String memberId,
            String lastRoomId,
            Long score,
            int limit
    );

    void updateLastReadSeq(
            String id,
            String memberId,
            Long lastReadSeq
    );

    void updateActivityScore(
            String id,
            String memberId,
            Long score
    );

    void recoverRoomUpdate(ChatRoom chatRoom, String oldTitle);

    void invalidateMembershipActivity(String id, String memberId);

    void invalidateRoomInfo(String id);
}
