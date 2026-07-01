package org.example.chat.chatroom.application.port.in;

import org.example.chat.chatroom.application.dto.ChatRoomUpdateCommand;
import org.example.chat.chatroom.application.service.command.ChatRoomCreateCommand;
import org.example.chat.chatroom.domain.model.ChatRoom;

public interface ChatRoomCommandUseCase {

    void create(ChatRoomCreateCommand command);

    void save(ChatRoom domain);

    void update(String roomId, ChatRoomUpdateCommand command);

    boolean join(String roomId, String memberId);

    void leave(String roomId, String memberId);

    void delete(String roomId);

    void activity(String id, String memberId, Long lastMsgSeq, Long lastMsgMs);
}
