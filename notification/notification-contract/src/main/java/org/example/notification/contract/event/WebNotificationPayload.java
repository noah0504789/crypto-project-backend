package org.example.notification.contract.event;

import java.util.List;
import java.util.Map;

public record WebNotificationPayload(
        String type,
        String title,
        String body,
        long createdAtMs,
        String link,
        List<WebNotificationMessagePart> messageParts,
        Map<String, Object> data
) {

    public WebNotificationPayload(
            String type,
            String title,
            String body,
            long createdAtMs,
            String link,
            Map<String, Object> data
    ) {
        this(type, title, body, createdAtMs, link, List.of(), data);
    }

    public WebNotificationPayload {
        messageParts = messageParts == null ? List.of() : List.copyOf(messageParts);
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static WebNotificationPayload withoutLink(
            String type,
            String title,
            String body,
            long createdAtMs,
            Map<String, Object> data
    ) {
        return withoutLink(type, title, body, createdAtMs, List.of(), data);
    }

    public static WebNotificationPayload withoutLink(
            String type,
            String title,
            String body,
            long createdAtMs,
            List<WebNotificationMessagePart> messageParts,
            Map<String, Object> data
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
