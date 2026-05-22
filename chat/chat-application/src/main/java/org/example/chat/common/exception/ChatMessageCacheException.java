package org.example.chat.common.exception;

import lombok.Getter;
import org.example.chatmessage.domain.model.ChatMessage;

@Getter
public class ChatMessageCacheException extends ChatCacheException {

    private final ChatMessage rollbackTarget;

    public ChatMessageCacheException(ChatMessage rollbackTarget, String message, Throwable cause) {
        super(message, cause);
        this.rollbackTarget = rollbackTarget;
    }
}