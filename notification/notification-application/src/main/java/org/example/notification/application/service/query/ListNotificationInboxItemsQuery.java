package org.example.notification.application.service.query;

import java.util.UUID;

public record ListNotificationInboxItemsQuery(
        UUID receiverId,
        String lastRecipientId,
        Long lastDeliveredAtMs,
        int limit
) {

    public static ListNotificationInboxItemsQuery firstPage(UUID receiverId, int limit) {
        return new ListNotificationInboxItemsQuery(receiverId, null, null, limit);
    }

    public static ListNotificationInboxItemsQuery prevPage(
        UUID receiverId,
        String lastRecipientId,
        Long lastDeliveredAtMs,
        int limit
    ) {
        return new ListNotificationInboxItemsQuery(receiverId, lastRecipientId, lastDeliveredAtMs, limit);
    }

    public boolean hasNoCursor() {
        return lastRecipientId == null || lastRecipientId.isBlank() || lastDeliveredAtMs == null;
    }

    public long cursorDeliveredAtMs() {
        return lastDeliveredAtMs == null ? 0L : lastDeliveredAtMs;
    }
}