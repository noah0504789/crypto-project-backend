package org.example.common.event.notification;

import org.example.common.event.TypedPayload;

import java.util.Map;

public record WebNotificationPayload(
        String type,
        String title,
        String body,
        long createdAt,
        String targetType,
        String targetId,
        Map<String, Object> data
) {

    public WebNotificationPayload {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static WebNotificationPayload fromTypedPayload(TypedPayload payload) {
        return new WebNotificationPayload(
                null,
                null,
                null,
                0L,
                null,
                null,
                payload.toMap()
        );
    }
}