package org.example.chatmessage.application.port.out;

import org.example.chatmessage.domain.model.ChatMessage;

import java.util.List;
import java.util.Optional;

public interface ChatMessagePersistencePort {

    List<ChatMessage> listLatest(String roomId, int limit);

    List<ChatMessage> listPrev(String roomId, String lastId, Long lastCreatedAtMillis, int limit);

    ChatMessage save(ChatMessage chatMessage);

    boolean hardDelete(String id);

    Optional<ChatMessage> findLatestExcluding(String roomId, String id);
}
