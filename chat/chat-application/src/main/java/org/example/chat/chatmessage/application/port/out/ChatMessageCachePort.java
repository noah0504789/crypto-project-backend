package org.example.chat.chatmessage.application.port.out;

import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatroom.application.service.result.ChatRoomMembershipScore;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;

import java.util.List;
import java.util.Set;

public interface ChatMessageCachePort {

    List<ChatMessage> listLatestMessages(String roomId, int limit);

    List<ChatMessage> listMessagesBefore(
            String roomId,
            String lastMsgId,
            Long lastCreatedAtMs,
            int limit
    );

    void save(
            ChatMessage message,
            ChatRoomCategory category,
            Set<String> memberIds
    );

    void warmUpList(List<ChatMessage> messages, String roomId);

    void hardDelete(
            String id,
            String roomId,
            List<ChatRoomMembershipScore> membershipScores
    );
}
