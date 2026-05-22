package org.example.chatroom.application.port.in;

import org.example.chatroom.application.dto.ChatRoomCreateRequest;
import org.example.chatroom.domain.model.ChatRoom;

import java.util.Map;

public interface ChatRoomCommandUseCase {

    void save(ChatRoom domain);

    void save(String hostId, ChatRoomCreateRequest request);

    void update(String roomId, Map<String, Object> updated);

    boolean join(String roomId, String memberId);

    void leave(String roomId, String memberId);

    void delete(String roomId);

    void activity(String id, String memberId, Long lastMsgSeq, Long lastMsgMs);
}
