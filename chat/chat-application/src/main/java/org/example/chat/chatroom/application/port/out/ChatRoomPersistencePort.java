package org.example.chat.chatroom.application.port.out;

import org.example.chat.chatroom.application.service.result.ChatRoomMembershipScore;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ChatRoomPersistencePort {
    ChatRoom save(ChatRoom chatRoom);

    void incrementMsgCnt(String id);

    void decrementMsgCnt(String id);

    ChatRoom updateAndReturn(String id, Map<String, Object> updated);

    void updateMembershipScores(String id, Set<String> memberIds, long lastMsgCreatedAt);

    List<ChatRoomMembershipScore> refreshMembershipScores(String id, long fallbackMsgCreatedAt);

    void active(String id, String memberId, Long lastMsgReadSeq, Long lastMsgCreatedAt);
    void leave(String id, String memberId);

    void join(String id, String memberId);

    void deleteById(String id);

    Optional<ChatRoom> findById(String id);

    Optional<ChatRoom> findByIdWithLatest(String id);

    boolean existsByTitle(String title);

    List<ChatRoom> listMostPopular(ChatRoomCategory category, int limit);

    List<ChatRoom> listNextPopular(ChatRoomCategory category, String lastId, Long lastPopularity, int limit);

    Long getLastReadSeq(String id, String memberId);

    List<ChatRoom> listLatestActive(String memberId, int limit);

    List<ChatRoom> listActiveBefore(String memberId, String lastId, Long score, int limit);
}
