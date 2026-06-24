package org.example.notification.domain.model;

import java.util.Objects;

public record NotificationMessagePart(
        String text,
        boolean bold,
        boolean lineBreakAfter
) {

    public NotificationMessagePart {
        text = Objects.requireNonNullElse(text, "");
    }

    public static NotificationMessagePart plain(String text) {
        return new NotificationMessagePart(text, false, false);
    }

    public static NotificationMessagePart bold(String text) {
        return new NotificationMessagePart(text, true, false);
    }
}