package org.example.chat.chatmessage.application.port.out;

import org.example.chat.chatmessage.domain.model.ChatMessage;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ChatMessagePersistencePort {

    ChatMessage save(ChatMessage chatMessage);

    Set<String> saveAll(Set<ChatMessage> chatMessages);

    boolean hardDeleteById(String id);

    Optional<ChatMessage> findLatestMessageExcluding(String roomId, String id);

    List<ChatMessage> listLatestMessages(String roomId, int limit);

    List<ChatMessage> listMessagesBefore(
            String roomId,
            String lastMsgId,
            Long lastCreatedAtMs,
            int limit
    );
}
