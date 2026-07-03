package org.example.notification.adapter.in.web.dto;

public record NotificationCursor(
        String lastRecipientId,
        Long lastDeliveredAtMs
) {

    public boolean isNull() {
        return lastRecipientId == null || lastDeliveredAtMs == null;
    }
}