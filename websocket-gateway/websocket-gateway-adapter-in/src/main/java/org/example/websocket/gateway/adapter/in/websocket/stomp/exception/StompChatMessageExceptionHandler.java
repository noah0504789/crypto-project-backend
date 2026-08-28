package org.example.websocket.gateway.adapter.in.websocket.stomp.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.example.websocket.gateway.adapter.in.websocket.stomp.dto.StompChatMessageAckResponse;
import org.example.websocket.gateway.adapter.in.websocket.stomp.dto.StompChatMessageSendRequest;
import org.example.websocket.gateway.adapter.in.websocket.stomp.ratelimit.ChatMessageRateLimitExceededException;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class StompChatMessageExceptionHandler {

    private static final String CLIENT_MESSAGE_ID = "clientMessageId";

    private final ObjectMapper objectMapper;

    @MessageExceptionHandler(ChatMessageRateLimitExceededException.class)
    @SendToUser("/queue/chat/ack")
    public StompChatMessageAckResponse handleRateLimitExceeded(ChatMessageRateLimitExceededException e) {
        log.warn("[stomp-rate-limit] chat message rejected");
        return StompChatMessageAckResponse.ofFailure(e.clientMessageId(), "RATE_LIMIT_EXCEEDED");
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser("/queue/chat/ack")
    public StompChatMessageAckResponse handleValidationException(MethodArgumentNotValidException e, Message<?> message) {
        log.warn("[stomp] validation error occurred: {}", e.getMessage());
        return StompChatMessageAckResponse.ofFailure(clientMessageIdOf(message), "VALIDATION_ERROR");
    }

    @MessageExceptionHandler(HandlerMethodValidationException.class)
    @SendToUser("/queue/chat/ack")
    public StompChatMessageAckResponse handleHandlerMethodValidationException(HandlerMethodValidationException e, Message<?> message) {
        log.warn("[stomp] handler method validation error occurred: {}", e.getMessage());
        return StompChatMessageAckResponse.ofFailure(clientMessageIdOf(message), "VALIDATION_ERROR");
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/chat/ack")
    public StompChatMessageAckResponse handleException(Exception e, Message<?> message) {
        log.error("[stomp] unexpected error occurred: {}", e.getMessage(), e);
        return StompChatMessageAckResponse.ofFailure(clientMessageIdOf(message), "SERVER_ERROR");
    }

    // 실패 ACK 에 어느 메시지인지가 없으면 발신자는 재전송할 대상을 고르지 못한다.
    // 변환 전 원문(byte[])일 수 있으므로 느슨하게 읽고, 못 읽으면 null 로 둔다.
    private String clientMessageIdOf(Message<?> message) {
        if (message == null) {
            return null;
        }

        Object payload = message.getPayload();

        try {
            if (payload instanceof StompChatMessageSendRequest request) {
                return request.clientMessageId();
            }

            JsonNode node = readTree(payload);

            return (node != null && node.hasNonNull(CLIENT_MESSAGE_ID)) ? node.get(CLIENT_MESSAGE_ID).asText() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode readTree(Object payload) throws java.io.IOException {
        if (payload instanceof byte[] bytes) {
            return objectMapper.readTree(bytes);
        }

        if (payload instanceof String text) {
            return objectMapper.readTree(text);
        }

        return null;
    }
}
