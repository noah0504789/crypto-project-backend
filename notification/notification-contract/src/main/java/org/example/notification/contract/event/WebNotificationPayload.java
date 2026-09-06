package org.example.notification.contract.event;

import java.util.List;

public record WebNotificationPayload(
        String type,
        String title,
        String body,
        long createdAtMs,
        String link,
        List<WebNotificationMessagePart> messageParts,
        PriceAlertData data
) {

    public WebNotificationPayload(
            String type,
            String title,
            String body,
            long createdAtMs,
            String link,
            PriceAlertData data
    ) {
        this(type, title, body, createdAtMs, link, List.of(), data);
    }

    public WebNotificationPayload {
        messageParts = messageParts == null ? List.of() : List.copyOf(messageParts);
    }

    public static WebNotificationPayload withoutLink(
            String type,
            String title,
            String body,
            long createdAtMs,
            PriceAlertData data
    ) {
        return withoutLink(type, title, body, createdAtMs, List.of(), data);
    }

    public static WebNotificationPayload withoutLink(
            String type,
            String title,
            String body,
            long createdAtMs,
            List<WebNotificationMessagePart> messageParts,
            PriceAlertData data
    ) {
        return new WebNotificationPayload(
                type,
                title,
                body,
                createdAtMs,
                null,
                messageParts,
                data
        );
    }
}
