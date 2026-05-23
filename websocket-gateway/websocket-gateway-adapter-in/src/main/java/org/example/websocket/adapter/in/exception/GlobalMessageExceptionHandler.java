package org.example.websocket.adapter.in.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.event.chatmessage.dto.ChatMessageAck;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalMessageExceptionHandler {

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser("/queue/chat/ack")
    public ChatMessageAck handleValidationException(MethodArgumentNotValidException e) {
        log.warn("STOMP validation error occurred: {}", e.getMessage());
        return ChatMessageAck.ofFailure(null, "VALIDATION_ERROR");
    }

    @MessageExceptionHandler(HandlerMethodValidationException.class)
    @SendToUser("/queue/chat/ack")
    public ChatMessageAck handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        log.warn("STOMP handler method validation error occurred: {}", e.getMessage());
        return ChatMessageAck.ofFailure(null, "VALIDATION_ERROR");
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/chat/ack")
    public ChatMessageAck handleException(Exception e) {
        log.error("STOMP unexpected error occurred: {}", e.getMessage(), e);
        return ChatMessageAck.ofFailure(null, "SERVER_ERROR");
    }
}
