package org.example.chat.chatroom.application.port.in;

import org.example.chat.chatroom.application.dto.ChatRoomCreateRequest;
import org.example.chat.chatroom.application.dto.ChatRoomUpdateCommand;
import org.example.chat.chatroom.domain.model.ChatRoom;

public interface ChatRoomCommandUseCase {

    void save(ChatRoom domain);

    void save(String hostId, ChatRoomCreateRequest request);

    void update(String roomId, ChatRoomUpdateCommand command);

    boolean join(String roomId, String memberId);

    void leave(String roomId, String memberId);

    void delete(String roomId);

    void activity(String id, String memberId, Long lastMsgSeq, Long lastMsgMs);
}
