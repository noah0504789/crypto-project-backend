package org.example.chat.chatmessage.adapter.in.exception;

public class ChatMessageResourceExhaustedException extends ChatMessageGrpcException {

    public ChatMessageResourceExhaustedException(String message, Throwable cause) {
        super(null, message, cause);
    }

    @Override
    public boolean requiresRollback() {
        return false;
    }
}