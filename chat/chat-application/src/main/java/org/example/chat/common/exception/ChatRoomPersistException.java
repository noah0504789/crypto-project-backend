package org.example.chat.common.exception;

import lombok.Getter;
import org.example.chat.chatroom.domain.model.ChatRoom;

@Getter
public class ChatRoomPersistException extends ChatPersistenceException {

    private final ChatRoom rollbackTarget;

    public ChatRoomPersistException(ChatRoom rollbackTarget, String message, Throwable cause) {
        super(message, cause);
        this.rollbackTarget = rollbackTarget;
    }
}