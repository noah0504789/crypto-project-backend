package org.example.websocket.gateway.stomp.dto;

import jakarta.validation.ConstraintViolation;
import org.example.common.validation.ValidationResult;

import java.util.Set;

public record ChatMessageAck(
        String id,
        String clientMessageId,
        boolean success,
        long ts,
        ValidationResult errors,
        String errorCode
) {

    public static ChatMessageAck ofSuccess(String id, String clientMessageId, boolean success, long ts) {
        return new ChatMessageAck(id, clientMessageId, success, ts, null, null);
    }

    public static <T> ChatMessageAck ofValidationError(String clientMessageId, Set<ConstraintViolation<T>> violations) {
        return new ChatMessageAck(null, clientMessageId, false, 0L, ValidationResult.fromConstraintViolations(violations), "VALIDATION_ERROR");
    }

    public static ChatMessageAck ofFailure(String clientMessageId, String errorCode) {
        return new ChatMessageAck(null, clientMessageId, false, 0L, null, errorCode);
    }
}
