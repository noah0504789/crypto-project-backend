package org.example.chat.chatroom.application.exception;

import lombok.Getter;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.exception.ChatPersistenceException;

@Getter
public class ChatRoomPersistException extends ChatPersistenceException {

    private final ChatRoom rollbackTarget;

    public ChatRoomPersistException(ChatRoom rollbackTarget, String message, Throwable cause) {
        super(message, cause);
        this.rollbackTarget = rollbackTarget;
    }
}