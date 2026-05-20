package org.example.common.exception;

import org.example.chatmessage.domain.model.ChatMessage;

public class ChatMessageGrpcCancelledException extends ChatMessageGrpcException {

    public ChatMessageGrpcCancelledException(ChatMessage rollbackTarget, String message) {
        super(rollbackTarget, message);
    }
}