package org.example.notification.contract.event;

public record WebNotificationMessagePart(
        String text,
        boolean bold,
        boolean lineBreakAfter
) {
}
