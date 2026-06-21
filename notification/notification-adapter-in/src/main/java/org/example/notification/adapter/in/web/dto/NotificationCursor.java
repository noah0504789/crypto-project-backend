package org.example.notification.adapter.in.web.dto;

public record NotificationCursor(
        String lastRecipientId,
        Long lastDeliveredAtMillis
) {

    public boolean isNull() {
        return lastRecipientId == null || lastDeliveredAtMillis == null;
    }
}