package org.example.chatmessage.application.port.in;

import org.example.chatmessage.domain.model.ChatMessage;

import java.util.List;

public interface ChatMessageQueryUsecase {
//    List<ChatMessage> findRecentByRoomId(String roomId, int offset, int limit);

    List<ChatMessage> listLatest(String roomId, int limit);

    List<ChatMessage> listPrev(String roomId, String lastId, Long lastCreatedAtMillis, int limit);
}
