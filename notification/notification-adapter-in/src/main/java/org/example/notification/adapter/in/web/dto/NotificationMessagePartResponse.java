package org.example.notification.adapter.in.web.dto;

import org.example.notification.domain.model.NotificationMessagePart;

public record NotificationMessagePartResponse(
        String text,
        boolean bold,
        boolean lineBreakAfter
) {

    public static NotificationMessagePartResponse from(NotificationMessagePart messagePart) {
        return new NotificationMessagePartResponse(
                messagePart.text(),
                messagePart.bold(),
                messagePart.lineBreakAfter()
        );
    }
}