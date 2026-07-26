package org.example.notification.adapter.in.web.dto;

import org.example.notification.domain.model.NotificationMessagePart;
import org.example.notification.application.service.result.NotificationInboxItem;

import java.time.LocalDateTime;
import java.util.List;

import static org.example.common.time.ServiceZoneUtils.ZONE_ID;

public record NotificationResponse(
        String id,
        String recipientId,
        String title,
        String message,
        List<NotificationMessagePartResponse> messageParts,
        boolean read,
        String readAt,
        String deliveredAt,
        Long deliveredAtMs,
        String createdAt,
        String link
) {

    public static NotificationResponse from(NotificationInboxItem item) {
        return new NotificationResponse(
                item.notificationId(),
                item.recipientId(),
                item.title(),
                item.message(),
                toMessagePartResponses(item.messageParts()),
                item.read(),
                toString(item.readAt()),
                toString(item.deliveredAt()),
                toEpochMillis(item.deliveredAt()),
                toString(item.createdAt()),
                item.link()
        );
    }

    // 커서 페이지네이션 계약: 다음 페이지 요청의 lastDeliveredAtMs로 그대로 되돌려 보낸다.
    // 저장 시 deliveredAt.atZone(ZONE_ID).toInstant()로 Instant가 되므로 동일 ZONE_ID로 정확히 복원된다.
    private static Long toEpochMillis(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        return dateTime.atZone(ZONE_ID).toInstant().toEpochMilli();
    }

    private static List<NotificationMessagePartResponse> toMessagePartResponses(List<NotificationMessagePart> messageParts) {
        if (messageParts == null) {
            return List.of();
        }

        return messageParts.stream()
                .map(NotificationMessagePartResponse::from)
                .toList();
    }

    private static String toString(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        return dateTime.toString();
    }
}