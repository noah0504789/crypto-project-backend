package org.example.chat.common.exception;

import lombok.Getter;
import org.example.chatmessage.domain.model.ChatMessage;

@Getter
public class ChatMessagePersistException extends ChatPersistenceException {

    private final ChatMessage rollbackTarget;

    public ChatMessagePersistException(ChatMessage rollbackTarget, String message, Throwable cause) {
        super(message, cause);
        this.rollbackTarget = rollbackTarget;
    }

}