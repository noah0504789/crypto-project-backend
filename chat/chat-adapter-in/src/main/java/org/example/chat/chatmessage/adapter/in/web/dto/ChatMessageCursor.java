package org.example.chat.chatmessage.adapter.in.web.dto;

// TODO: validation check
public record ChatMessageCursor(
        String lastMsgId,
        Long lastCreatedAtMs
) {

    public boolean isNull() {
        return lastMsgId == null && lastCreatedAtMs == null;
    }
}
