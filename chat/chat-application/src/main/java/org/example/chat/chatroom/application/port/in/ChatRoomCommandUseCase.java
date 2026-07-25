package org.example.chat.chatroom.application.port.in;

import org.example.chat.chatroom.application.service.command.ChatRoomActivityCommand;
import org.example.chat.chatroom.application.service.command.ChatRoomUpdateCommand;
import org.example.chat.chatroom.application.service.command.ChatRoomCreateCommand;
import org.example.chat.chatroom.domain.model.ChatRoom;

public interface ChatRoomCommandUseCase {

    void create(ChatRoomCreateCommand command);

    void save(ChatRoom domain);

    void update(ChatRoomUpdateCommand command);

    boolean join(String roomId, String memberId);

    void leave(String roomId, String memberId);

    void delete(String roomId, String myUserId);

    void activity(ChatRoomActivityCommand command);
}
