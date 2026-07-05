package org.example.notification.contract.event;

import org.example.common.event.TypedPayload;

public record WebNotificationPayload(
        String type,
        String title,
        String body,
        long createdAtMs,
        String link,
        TypedPayload typedPayload
) {

    public WebNotificationPayload {
        typedPayload = typedPayload == null ? TypedPayload.empty() : typedPayload;
    }

    public static WebNotificationPayload withoutLink(
            String type,
            String title,
            String body,
            long createdAtMs,
            TypedPayload typedPayload
    ) {
        return new WebNotificationPayload(
                type,
                title,
                body,
                createdAtMs,
                null,
                typedPayload
        );
    }
}