package org.example.notification.contract.event;

import org.example.common.event.TypedPayload;

import java.util.List;

public record WebNotificationPayload(
        String type,
        String title,
        String body,
        long createdAtMs,
        String link,
        List<WebNotificationMessagePart> messageParts,
        TypedPayload typedPayload
) {

    public WebNotificationPayload(
            String type,
            String title,
            String body,
            long createdAtMs,
            String link,
            TypedPayload typedPayload
    ) {
        this(type, title, body, createdAtMs, link, List.of(), typedPayload);
    }

    public WebNotificationPayload {
        messageParts = messageParts == null ? List.of() : List.copyOf(messageParts);
        typedPayload = typedPayload == null ? TypedPayload.empty() : typedPayload;
    }

    public static WebNotificationPayload withoutLink(
            String type,
            String title,
            String body,
            long createdAtMs,
            TypedPayload typedPayload
    ) {
        return withoutLink(type, title, body, createdAtMs, List.of(), typedPayload);
    }

    public static WebNotificationPayload withoutLink(
            String type,
            String title,
            String body,
            long createdAtMs,
            List<WebNotificationMessagePart> messageParts,
            TypedPayload typedPayload
    ) {
        return new WebNotificationPayload(
                type,
                title,
                body,
                createdAtMs,
                null,
                messageParts,
                typedPayload
        );
    }
}
