package org.example.common.exception;

import org.example.chatmessage.domain.model.ChatMessage;

public class ChatMessagePersistException extends ChatMessageGrpcException {

    public ChatMessagePersistException(ChatMessage rollbackTarget, String message, Throwable cause) {
        super(rollbackTarget, message, cause);
    }
}