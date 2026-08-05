package org.example.websocket.gateway.notification.adapter.out.stomp.payload;

import org.example.websocket.gateway.notification.application.service.command.WebNotificationCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StompWebNotificationPayloadUnitTest {

    @Test
    @DisplayName("웹 알림 command의 notificationId를 STOMP payload에 포함한다")
    void from_includeNotificationId() {
        // given
        WebNotificationCommand command = new WebNotificationCommand(
                "receiver-id",
                "notification-id",
                "PRICE_ALERT",
                "가격 알림",
                "KRW-BTC 가격이 변동했습니다.",
                1_000L,
                "/price-alerts",
                Map.of()
        );

        // when
        StompWebNotificationPayload result = StompWebNotificationPayload.from(command);

        // then
        assertThat(result.notificationId()).isEqualTo("notification-id");
    }
}
