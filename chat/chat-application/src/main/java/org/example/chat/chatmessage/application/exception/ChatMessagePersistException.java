package org.example.chat.chatmessage.application.exception;

import lombok.Getter;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.exception.ChatPersistenceException;

@Getter
public class ChatMessagePersistException extends ChatPersistenceException {

    private final ChatMessage rollbackTarget;

    public ChatMessagePersistException(ChatMessage rollbackTarget, String message, Throwable cause) {
        super(message, cause);
        this.rollbackTarget = rollbackTarget;
    }

}