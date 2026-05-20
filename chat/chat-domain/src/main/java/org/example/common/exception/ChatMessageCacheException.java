package org.example.common.exception;

import org.example.chatmessage.domain.model.ChatMessage;

public class ChatMessageCacheException extends ChatMessageGrpcException {

    public ChatMessageCacheException(ChatMessage rollbackTarget, String message, Throwable cause) {
        super(rollbackTarget, message, cause);
    }
}