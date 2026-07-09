package org.example.chat.chatroom.application.port.out;

import org.example.chat.chatroom.application.service.result.ChatRoomMembershipScore;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ChatRoomPersistencePort {

    Optional<ChatRoom> findById(String id);

    Optional<ChatRoom> findByIdWithLatestMessage(String id);

    List<ChatRoom> listPopularRooms(ChatRoomCategory category, int limit);

    List<ChatRoom> listPopularRoomsAfter(
            ChatRoomCategory category,
            String lastRoomId,
            Long lastPopularity,
            int limit
    );

    List<ChatRoom> listLatestActiveRooms(String memberId, int limit);

    List<ChatRoom> listActiveRoomsBefore(
            String memberId,
            String lastRoomId,
            Long score,
            int limit
    );

    Long getLastReadSeq(String id, String memberId);

    boolean existsByTitle(String title);


    ChatRoom save(ChatRoom chatRoom);

    ChatRoom updateRoomAndReturn(String id, Map<String, Object> updates);

    void incrementMessageCount(String id);

    void decrementMessageCount(String id);

    void updateMembershipScores(
            String id,
            Set<String> memberIds,
            long lastMsgCreatedAtMs
    );

    List<ChatRoomMembershipScore> refreshMembershipScores(String id, long fallbackMsgCreatedAtMs);

    void activateMembership(
            String id,
            String memberId,
            Long lastMsgReadSeq,
            Long lastMsgCreatedAtMs
    );

    void joinMembership(String id, String memberId);

    void leaveMembership(String id, String memberId);

    void deleteById(String id);
}
