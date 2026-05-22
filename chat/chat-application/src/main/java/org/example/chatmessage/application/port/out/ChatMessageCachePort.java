package org.example.chatmessage.application.port.out;

import org.example.chatmessage.domain.model.ChatMessage;
import org.example.chatroom.application.dto.ChatRoomMembershipScore;
import org.example.chatroom.domain.model.ChatRoomCategory;

import java.util.List;
import java.util.Set;

public interface ChatMessageCachePort {

    void save(ChatMessage domain, ChatRoomCategory category, Set<String> memberIds);

    void warmUpList(List<ChatMessage> list, String roomId);

    List<ChatMessage> listLatest(String roomId, int limit);

    List<ChatMessage> listPrev(String roomId, String lastId, Long lastCreatedAtMillis, int limit);

    void hardDelete(String id, String roomId, List<ChatRoomMembershipScore> chatRoomMembershipScores);
}
