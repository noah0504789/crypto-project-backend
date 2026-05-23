package event;

import org.example.common.enums.StompTopic;
import org.example.common.event.notification.WebNotificationEvent;
import org.example.common.event.notification.WebNotificationPayload;
import org.example.websocket.adapter.in.event.notification.WebNotificationEventConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebNotificationEventService")
class WebNotificationEventConsumerTest {

    @Mock
    private SimpMessagingTemplate stompTemplate;

    @InjectMocks
    private WebNotificationEventConsumer sut;

    private final String txId = "tx-1";
    private final String eventType = "WEB_NOTIFICATION";
    private final String partitionKey = "user-1";
    private final WebNotificationPayload payload = new WebNotificationPayload(
            "CHAT",
            "새 메시지",
            "새 메시지가 도착했습니다.",
            1_767_225_600_000L,
            "CHAT_ROOM",
            "room-1"
    );

    @Test
    @DisplayName("웹 알림 이벤트를 받으면 partitionKey 기반 destination으로 payload를 전송한다")
    void handleSendWebNotification() {
        // given
        WebNotificationEvent event = event(payload, partitionKey);

        // when
        sut.handle(event, txId);

        // then
        verify(stompTemplate).convertAndSend(
                StompTopic.NOTIFICATION.destination(partitionKey),
                payload
        );
    }

    @Test
    @DisplayName("STOMP 전송 중 예외가 발생해도 예외를 밖으로 던지지 않는다")
    void handleDoesNotThrowWhenStompSendFails() {
        // given
        WebNotificationEvent event = event(payload, partitionKey);

        doThrow(new RuntimeException("stomp failed"))
                .when(stompTemplate)
                .convertAndSend(
                        StompTopic.NOTIFICATION.destination(partitionKey),
                        payload
                );

        // when & then
        assertDoesNotThrow(() -> sut.handle(event, txId));

        verify(stompTemplate).convertAndSend(
                StompTopic.NOTIFICATION.destination(partitionKey),
                payload
        );
    }

    @Test
    @DisplayName("payload가 null이면 STOMP 전송을 하지 않는다")
    void handleSkipWhenPayloadIsNull() {
        // given
        WebNotificationEvent event = event(null, partitionKey);

        // when
        sut.handle(event, txId);

        // then
        verifyNoInteractions(stompTemplate);
    }

    @Test
    @DisplayName("partitionKey가 null이면 STOMP 전송을 하지 않는다")
    void handleSkipWhenPartitionKeyIsNull() {
        // given
        WebNotificationEvent event = event(payload, null);

        // when
        sut.handle(event, txId);

        // then
        verifyNoInteractions(stompTemplate);
    }

    @Test
    @DisplayName("partitionKey가 blank이면 STOMP 전송을 하지 않는다")
    void handleSkipWhenPartitionKeyIsBlank() {
        // given
        WebNotificationEvent event = event(payload, "   ");

        // when
        sut.handle(event, txId);

        // then
        verifyNoInteractions(stompTemplate);
    }

    private WebNotificationEvent event(WebNotificationPayload payload, String partitionKey) {
        return new WebNotificationEvent(eventType, payload, partitionKey);
    }
}
