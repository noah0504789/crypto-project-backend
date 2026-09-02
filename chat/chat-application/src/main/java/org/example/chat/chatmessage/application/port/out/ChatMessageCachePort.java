package org.example.chat.chatmessage.application.port.out;

import org.example.chat.chatmessage.domain.model.ChatMessage;

import java.util.List;

public interface ChatMessageCachePort {

    List<ChatMessage> listLatestMessages(String roomId, int limit);

    List<ChatMessage> listMessagesBefore(
            String roomId,
            String lastMsgId,
            Long lastCreatedAtMs,
            int limit
    );

    void save(ChatMessage message);

    void warmUpList(List<ChatMessage> messages, String roomId);

    void hardDelete(
            String id,
            String roomId,
            long fallbackMsgCreatedAtMs
    );
}
