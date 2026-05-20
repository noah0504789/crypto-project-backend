package org.example.chatmessage.domain.util;

import org.example.chatmessage.domain.model.ChatMessage;

import java.util.Comparator;

public final class ChatMessageComparators {
    private ChatMessageComparators() {}

    public static final Comparator<ChatMessage> BY_ID_DESC = Comparator.comparing(ChatMessage::getId).reversed();
    public static final Comparator<ChatMessage> BY_CREATED_AT_DESC_THEN_ID_DESC = Comparator.comparingLong(ChatMessage::toEpochMillis).reversed().thenComparing(ChatMessage::getId, Comparator.reverseOrder());
}
