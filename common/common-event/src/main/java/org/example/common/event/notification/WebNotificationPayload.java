package org.example.common.event.notification;

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

    public WebNotificationPayload(
            String type,
            String title,
            String body,
            long createdAt,
            String targetType,
            String targetId
    ) {
        this(type, title, body, createdAt, targetType, targetId, Map.of());
    }

    public static WebNotificationPayload fromData(Map<String, Object> data) {
        return new WebNotificationPayload(null, null, null, 0L, null, null, Map.copyOf(data));
    }
}
