package org.example.chatmessage.adapter.dto;

// TODO: validation check
public record ChatMessageCursor(
        String lastId,
        Long lastCreatedAtMillis
) {

    public boolean isNull() {
        return lastId == null && lastCreatedAtMillis == null;
    }
}
