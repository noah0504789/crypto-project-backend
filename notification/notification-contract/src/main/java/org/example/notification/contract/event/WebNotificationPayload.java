package org.example.notification.contract.event;

import org.example.common.event.TypedPayload;

public record WebNotificationPayload(
        String type,
        String title,
        String body,
        long createdAt,
        String targetType,
        String targetId,
        TypedPayload data
) {

    public WebNotificationPayload {
        data = data == null ? TypedPayload.empty() : data;
    }

    public static WebNotificationPayload of(
            String type,
            String title,
            String body,
            long createdAt,
            String targetType,
            String targetId,
            TypedPayload data
    ) {
        return new WebNotificationPayload(
                type,
                title,
                body,
                createdAt,
                targetType,
                targetId,
                data
        );
    }
}