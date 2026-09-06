package org.example.notification.application.event.mapper;

import org.example.notification.contract.event.PriceAlertPayload;
import org.example.notification.contract.event.WebNotificationPayload;
import org.example.notification.domain.model.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebNotificationPayloadMapperUnitTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 10, 0);
    private static final PriceAlertPayload PRICE_ALERT_PAYLOAD =
            new PriceAlertPayload("KRW-BTC", 105D, 100D, 5, 0.05, "PERCENT_5", 1_757_000_000_000L);

    @Test
    @DisplayName("알림 도메인의 표시 정보와 탐지 원본을 웹 푸시 계약으로 옮긴다")
    void from_shouldMapDisplayFieldsAndData() {
        Notification notification = priceAlertNotification();

        WebNotificationPayload payload = WebNotificationPayloadMapper.from(notification, PRICE_ALERT_PAYLOAD);

        assertThat(payload.type()).isEqualTo("PRICE_ALERT");
        assertThat(payload.title()).isEqualTo(notification.getTitle());
        assertThat(payload.body()).isEqualTo(notification.getMessage());
        assertThat(payload.createdAtMs()).isEqualTo(notification.getCreatedAtMs());
        assertThat(payload.link()).isNull();
        assertThat(payload.data()).isEqualTo(PRICE_ALERT_PAYLOAD);
    }

    @Test
    @DisplayName("도메인 메시지 조각을 계약 타입으로 하나씩 옮긴다")
    void from_shouldConvertEveryMessagePart() {
        Notification notification = priceAlertNotification();

        WebNotificationPayload payload = WebNotificationPayloadMapper.from(notification, PRICE_ALERT_PAYLOAD);

        assertThat(payload.messageParts()).hasSameSizeAs(notification.getMessageParts());
        assertThat(payload.messageParts())
                .extracting("text", "bold", "lineBreakAfter")
                .containsExactlyElementsOf(
                        notification.getMessageParts().stream()
                                .map(part -> org.assertj.core.groups.Tuple.tuple(
                                        part.text(), part.bold(), part.lineBreakAfter()))
                                .toList()
                );
    }

    private Notification priceAlertNotification() {
        return Notification.createPriceAlert(
                "notification-1",
                "KRW-BTC",
                105D,
                100D,
                5,
                0.05,
                Map.of("code", "KRW-BTC"),
                CREATED_AT
        );
    }
}
