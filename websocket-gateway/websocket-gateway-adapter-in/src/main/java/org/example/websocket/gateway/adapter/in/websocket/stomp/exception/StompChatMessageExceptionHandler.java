package org.example.websocket.gateway.adapter.in.websocket.stomp.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.example.websocket.gateway.adapter.in.websocket.stomp.dto.StompChatMessageAckResponse;
import org.example.websocket.gateway.adapter.in.websocket.stomp.ratelimit.ChatMessageRateLimitExceededException;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class StompChatMessageExceptionHandler {

    @MessageExceptionHandler(ChatMessageRateLimitExceededException.class)
    @SendToUser("/queue/chat/ack")
    public StompChatMessageAckResponse handleRateLimitExceeded(ChatMessageRateLimitExceededException e) {
        log.warn("[stomp-rate-limit] chat message rejected");
        return StompChatMessageAckResponse.ofFailure(e.clientMessageId(), "RATE_LIMIT_EXCEEDED");
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser("/queue/chat/ack")
    public StompChatMessageAckResponse handleValidationException(MethodArgumentNotValidException e) {
        log.warn("[stomp] validation error occurred: {}", e.getMessage());
        return StompChatMessageAckResponse.ofFailure(null, "VALIDATION_ERROR");
    }

    @MessageExceptionHandler(HandlerMethodValidationException.class)
    @SendToUser("/queue/chat/ack")
    public StompChatMessageAckResponse handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        log.warn("[stomp] handler method validation error occurred: {}", e.getMessage());
        return StompChatMessageAckResponse.ofFailure(null, "VALIDATION_ERROR");
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/chat/ack")
    public StompChatMessageAckResponse handleException(Exception e) {
        log.error("[stomp] unexpected error occurred: {}", e.getMessage(), e);
        return StompChatMessageAckResponse.ofFailure(null, "SERVER_ERROR");
    }
}
