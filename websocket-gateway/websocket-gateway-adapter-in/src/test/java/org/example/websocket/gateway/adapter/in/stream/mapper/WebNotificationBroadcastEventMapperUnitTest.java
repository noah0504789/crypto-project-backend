package org.example.websocket.gateway.adapter.in.stream.mapper;

import org.example.common.event.TypedPayload;
import org.example.notification.contract.event.WebNotificationBroadcastEvent;
import org.example.notification.contract.event.WebNotificationPayload;
import org.example.websocket.gateway.notification.application.service.command.WebNotificationCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebNotificationBroadcastEventMapperUnitTest {

    private final WebNotificationBroadcastEventMapper sut = new WebNotificationBroadcastEventMapper();

    @Test
    @DisplayName("웹 알림 이벤트의 notificationId를 전송 command에 매핑한다")
    void toCommand_mapNotificationId() {
        // given
        WebNotificationPayload payload = new WebNotificationPayload(
                "PRICE_ALERT",
                "가격 알림",
                "KRW-BTC 가격이 변동했습니다.",
                1_000L,
                "/price-alerts",
                TypedPayload.empty()
        );
        WebNotificationBroadcastEvent event = WebNotificationBroadcastEvent.of(payload, "notification-id", "receiver-id");

        // when
        WebNotificationCommand result = sut.toCommand(event);

        // then
        assertThat(result.notificationId()).isEqualTo("notification-id");
        assertThat(result.receiverId()).isEqualTo("receiver-id");
    }
}
