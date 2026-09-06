package org.example.websocket.gateway.notification.adapter.out.stomp;

import org.example.websocket.gateway.notification.adapter.out.stomp.payload.StompWebNotificationPayload;
import org.example.notification.contract.event.PriceAlertPayload;
import org.example.websocket.gateway.notification.application.service.command.WebNotificationCommand;
import org.example.websocket.gateway.session.application.cache.LocalSessionCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("STOMP 웹 알림 어댑터")
class StompWebNotificationAdapterUnitTest {

    private static final String RECEIVER_ID = "receiver-id";

    @Mock
    private SimpMessagingTemplate stompTemplate;

    @Mock
    private LocalSessionCache localSessionCache;

    private StompWebNotificationAdapter sut;

    @BeforeEach
    void setUp() {
        sut = new StompWebNotificationAdapter(stompTemplate, localSessionCache);
    }

    @Test
    @DisplayName("알림은 사용자 queue destination으로 전송한다")
    void sendsNotificationToUserQueueDestination() {
        WebNotificationCommand command = new WebNotificationCommand(
                RECEIVER_ID,
                "notification-id",
                "PRICE_ALERT",
                "title",
                "body",
                1_000L,
                "/markets/KRW-BTC",
                List.of(),
                new PriceAlertPayload("KRW-BTC", 105D, 100D, 5, 0.05, "PERCENT_5", 1_757_000_000_000L)
        );
        given(localSessionCache.hasUser(RECEIVER_ID)).willReturn(true);

        boolean result = sut.send(command, "tx-id");

        assertThat(result).isTrue();
        verify(stompTemplate).convertAndSendToUser(
                eq(RECEIVER_ID),
                eq("/queue/notification"),
                any(StompWebNotificationPayload.class)
        );
    }
}
