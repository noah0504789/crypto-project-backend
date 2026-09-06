package org.example.websocket.gateway.notification.adapter.out.stomp.payload;

import org.example.notification.contract.event.PriceAlertData;
import org.example.websocket.gateway.notification.application.service.command.WebNotificationCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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
                List.of(),
                new PriceAlertData("KRW-BTC", 105D, 100D, 5, 0.05, "PERCENT_5", 1_757_000_000_000L)
        );

        // when
        StompWebNotificationPayload result = StompWebNotificationPayload.from(command);

        // then
        assertThat(result.notificationId()).isEqualTo("notification-id");
    }
}
