package org.example.chat.chatmessage.adapter.in.exception;

import org.example.chat.chatmessage.domain.model.ChatMessage;

public class ChatMessageGrpcCancelledException extends ChatMessageGrpcException {

    public ChatMessageGrpcCancelledException(ChatMessage rollbackTarget, String message) {
        super(rollbackTarget, message);
    }
}