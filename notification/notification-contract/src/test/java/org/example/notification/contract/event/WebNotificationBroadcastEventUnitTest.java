package org.example.notification.contract.event;

import org.example.common.outbox.domain.OutboxDispatchType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebNotificationBroadcastEventUnitTest {

    @Test
    @DisplayName("웹 알림 이벤트는 브로드캐스트 레인으로 발행한다")
    void getDispatchType_shouldReturnBroadcast() {
        WebNotificationPayload payload = new WebNotificationPayload(
                "PRICE_ALERT",
                "title",
                "body",
                0L,
                null,
                null
        );

        WebNotificationBroadcastEvent event = WebNotificationBroadcastEvent.of(
                payload,
                "notification-id",
                "receiver-id"
        );

        assertThat(event.getDispatchType()).isEqualTo(OutboxDispatchType.BROADCAST);
    }
}
