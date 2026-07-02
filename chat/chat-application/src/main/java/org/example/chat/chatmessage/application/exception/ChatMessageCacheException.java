package org.example.chat.chatmessage.application.exception;

import lombok.Getter;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.exception.ChatCacheException;

@Getter
public class ChatMessageCacheException extends ChatCacheException {

    private final ChatMessage rollbackTarget;

    public ChatMessageCacheException(ChatMessage rollbackTarget, String message, Throwable cause) {
        super(message, cause);
        this.rollbackTarget = rollbackTarget;
    }
}