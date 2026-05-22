package org.example.chatmessage.application.dto;

// TODO: validation check
public record ChatMessageCursor(
        String lastId,
        Long lastCreatedAtMillis
) {

    public boolean isNull() {
        return lastId == null && lastCreatedAtMillis == null;
    }
}
