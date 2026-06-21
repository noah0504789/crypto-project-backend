package org.example.notification.adapter.out.persistence;

import org.example.notification.domain.model.NotificationMessagePart;

import java.util.Objects;

public record MongoNotificationMessagePart(
        String text,
        boolean bold,
        boolean lineBreakAfter
) {

    public MongoNotificationMessagePart {
        text = Objects.requireNonNullElse(text, "");
    }

    public static MongoNotificationMessagePart fromDomain(NotificationMessagePart messagePart) {
        return new MongoNotificationMessagePart(
                messagePart.text(),
                messagePart.bold(),
                messagePart.lineBreakAfter()
        );
    }

    public NotificationMessagePart toDomain() {
        return new NotificationMessagePart(
                text,
                bold,
                lineBreakAfter
        );
    }
}

