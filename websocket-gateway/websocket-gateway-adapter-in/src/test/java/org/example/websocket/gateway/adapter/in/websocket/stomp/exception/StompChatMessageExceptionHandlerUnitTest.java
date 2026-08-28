package org.example.websocket.gateway.adapter.in.websocket.stomp.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.websocket.gateway.adapter.in.websocket.stomp.dto.StompChatMessageAckResponse;
import org.example.websocket.gateway.adapter.in.websocket.stomp.dto.StompChatMessageSendRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("실패 ACK 는 어느 메시지가 실패했는지 담는다")
class StompChatMessageExceptionHandlerUnitTest {

    private StompChatMessageExceptionHandler sut;

    private final String clientMessageId = "client-1";

    @BeforeEach
    void setUp() {
        sut = new StompChatMessageExceptionHandler(new ObjectMapper());
    }

    private Message<?> messageOf(Object payload) {
        return MessageBuilder.withPayload(payload).build();
    }

    @Test
    @DisplayName("변환 전 원문(byte[])에서도 clientMessageId 를 읽는다")
    void readsFromRawBytes() {
        // given
        String body = "{\"clientMessageId\":\"" + clientMessageId + "\",\"roomId\":\"r1\"}";

        // when
        StompChatMessageAckResponse response =
                sut.handleException(new IllegalStateException("boom"), messageOf(body.getBytes(StandardCharsets.UTF_8)));

        // then
        assertThat(response.clientMessageId()).isEqualTo(clientMessageId);
        assertThat(response.errorCode()).isEqualTo("SERVER_ERROR");
    }

    @Test
    @DisplayName("이미 변환된 요청에서도 읽는다")
    void readsFromConvertedRequest() {
        // given
        StompChatMessageSendRequest request =
                new StompChatMessageSendRequest(clientMessageId, "r1", "w1", "내용");

        // when
        StompChatMessageAckResponse response =
                sut.handleException(new IllegalStateException("boom"), messageOf(request));

        // then
        assertThat(response.clientMessageId()).isEqualTo(clientMessageId);
    }

    @Test
    @DisplayName("읽을 수 없으면 null 로 두고 ACK 자체는 보낸다")
    void keepsNullWhenUnreadable() {
        // when
        StompChatMessageAckResponse response = sut.handleException(
                new IllegalStateException("boom"),
                messageOf("깨진 payload".getBytes(StandardCharsets.UTF_8))
        );

        // then
        assertThat(response.clientMessageId()).isNull();
        assertThat(response.errorCode()).isEqualTo("SERVER_ERROR");
    }
}
