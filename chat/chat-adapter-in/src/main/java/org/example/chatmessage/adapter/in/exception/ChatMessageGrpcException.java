package org.example.chatmessage.adapter.in.exception;

import lombok.Getter;
import org.example.chatmessage.domain.model.ChatMessage;
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