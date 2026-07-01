package org.example.chat.chatmessage.adapter.in.grpc.exception;

import lombok.Getter;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.common.exception.InfrastructureException;

@Getter
public abstract class ChatMessageGrpcException extends InfrastructureException {

    private final ChatMessage rollbackTarget;

    protected ChatMessageGrpcException(ChatMessage rollbackTarget, String message) {
        super(message);
        this.rollbackTarget = rollbackTarget;
    }

    protected ChatMessageGrpcException(ChatMessage rollbackTarget, String message, Throwable cause) {
        super(message, cause);
        this.rollbackTarget = rollbackTarget;
    }

    public boolean requiresRollback() {
        return rollbackTarget != null;
    }
}