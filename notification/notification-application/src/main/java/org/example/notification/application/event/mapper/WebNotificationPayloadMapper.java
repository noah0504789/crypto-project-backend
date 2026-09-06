package org.example.notification.application.event.mapper;

import org.example.notification.contract.event.PriceAlertPayload;
import org.example.notification.contract.event.WebNotificationMessagePart;
import org.example.notification.contract.event.WebNotificationPayload;
import org.example.notification.domain.model.Notification;

import java.util.List;

/**
 * 알림 도메인을 웹 푸시 계약으로 옮긴다.
 * 계약 타입은 도메인을 알 수 없어(contract → domain 의존 금지) 변환을 application 에 둔다.
 */
public final class WebNotificationPayloadMapper {

    private WebNotificationPayloadMapper() {
    }

    public static WebNotificationPayload from(Notification notification, PriceAlertPayload data) {
        return WebNotificationPayload.withoutLink(
                notification.getType().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getCreatedAtMs(),
                toMessageParts(notification),
                data
        );
    }

    private static List<WebNotificationMessagePart> toMessageParts(Notification notification) {
        return notification.getMessageParts().stream()
                .map(part -> new WebNotificationMessagePart(part.text(), part.bold(), part.lineBreakAfter()))
                .toList();
    }
}
