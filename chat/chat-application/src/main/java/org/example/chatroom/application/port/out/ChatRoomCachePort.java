package org.example.chatroom.application.port.out;

import org.example.chatroom.domain.model.ChatRoom;
import org.example.chatroom.domain.model.ChatRoomCategory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ChatRoomCachePort {

    void save(ChatRoom room);

    void warmUp(ChatRoom room);

    void warmUpList(List<ChatRoom> rooms, Map<String, Double> popularities);

    void update(String id, Map<String, Object> updated, String oldTitle);

    void join(String id, String memberId);

    boolean leave(String id, String memberId);

    void delete(String id, ChatRoomCategory category, String title, Set<String> memberIds);

    Optional<Long> getLastMsgSeq(String roomId, String memberId);

    Optional<ChatRoom> findById(String id);

    Optional<Boolean> existsByTitle(String title);

    List<ChatRoom> listMostPopular(ChatRoomCategory category, int limit);

    List<ChatRoom> listNextPopular(ChatRoomCategory category, String lastId, Long lastPopularity, int limit);

    List<ChatRoom> listLatestActive(String memberId, int limit);

    List<ChatRoom> listActiveBefore(String memberId, String lastId, Long score, int limit);

    void updateLastRead(String id, String memberId, Long lastMsgSeq);

    void updateRecentScore(String id, String memberId, Long lastMsgMs);

    void recoverUpdate(ChatRoom chatRoom, String oldTitle);

    void invalidateActivity(String id, String memberId);

    void invalidateInfo(String id);
}
