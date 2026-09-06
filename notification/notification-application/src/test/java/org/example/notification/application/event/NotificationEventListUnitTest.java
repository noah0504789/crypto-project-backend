package org.example.notification.application.event;

import org.example.notification.application.event.payload.NotificationRecipientPayload;
import org.example.notification.contract.event.PriceAlertPayload;
import org.example.notification.contract.event.WebNotificationBroadcastEvent;
import org.example.notification.domain.model.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventListUnitTest {

    private static final String NOTIFICATION_ID = "notification-1";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 10, 0);
    private static final UUID RECEIVER_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RECEIVER_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final PriceAlertPayload PRICE_ALERT_PAYLOAD =
            new PriceAlertPayload("KRW-BTC", 105D, 100D, 5, 0.05, "PERCENT_5", 1_757_000_000_000L);

    @Test
    @DisplayName("영속 이벤트는 1건, 전달 이벤트는 수신자마다 1건 만든다")
    void forPriceAlert_shouldCreateOneSaveEventAndOneWebEventPerReceiver() {
        List<NotificationRecipientPayload> recipients = recipientPayloads();

        NotificationEventList eventList =
                NotificationEventList.forPriceAlert(notification(), recipients, PRICE_ALERT_PAYLOAD);

        assertThat(eventList.getEventList()).hasSize(3);
        assertThat(eventList.getEventList().get(0)).isInstanceOf(NotificationSaveEvent.class);
        assertThat(eventList.getEventList().subList(1, 3))
                .allMatch(WebNotificationBroadcastEvent.class::isInstance);
    }

    @Test
    @DisplayName("전달 이벤트의 partitionKey 는 게이트웨이가 읽는 수신자 id 다")
    void forPriceAlert_shouldRouteWebEventByReceiverId() {
        NotificationEventList eventList =
                NotificationEventList.forPriceAlert(notification(), recipientPayloads(), PRICE_ALERT_PAYLOAD);

        assertThat(eventList.getEventList().subList(1, 3))
                .extracting(event -> ((WebNotificationBroadcastEvent) event).getPartitionKey())
                .containsExactly(RECEIVER_1.toString(), RECEIVER_2.toString());
    }

    @Test
    @DisplayName("전달 이벤트는 알림 본문과 탐지 원본을 함께 싣는다")
    void forPriceAlert_shouldCarryNotificationBodyAndDetectionData() {
        NotificationEventList eventList =
                NotificationEventList.forPriceAlert(notification(), recipientPayloads(), PRICE_ALERT_PAYLOAD);

        WebNotificationBroadcastEvent webEvent =
                (WebNotificationBroadcastEvent) eventList.getEventList().get(1);

        assertThat(webEvent.getNotificationId()).isEqualTo(NOTIFICATION_ID);
        assertThat(webEvent.getPayload().type()).isEqualTo("PRICE_ALERT");
        assertThat(webEvent.getPayload().title()).isEqualTo("가격 알림");
        assertThat(webEvent.getPayload().messageParts()).isNotEmpty();
        assertThat(webEvent.getPayload().messageParts().get(0).text()).isEqualTo("KRW-BTC");
        assertThat(webEvent.getPayload().messageParts().get(0).bold()).isTrue();
        assertThat(webEvent.getPayload().data()).isEqualTo(PRICE_ALERT_PAYLOAD);
    }

    private Notification notification() {
        return Notification.createPriceAlert(
                NOTIFICATION_ID,
                "KRW-BTC",
                105D,
                100D,
                5,
                0.05,
                java.util.Map.of("code", "KRW-BTC"),
                CREATED_AT
        );
    }

    private List<NotificationRecipientPayload> recipientPayloads() {
        return List.of(
                NotificationRecipientPayload.of("recipient-1", NOTIFICATION_ID, RECEIVER_1, CREATED_AT),
                NotificationRecipientPayload.of("recipient-2", NOTIFICATION_ID, RECEIVER_2, CREATED_AT)
        );
    }
}
