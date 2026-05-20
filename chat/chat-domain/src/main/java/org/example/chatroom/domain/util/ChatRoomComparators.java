package org.example.chatroom.domain.util;

import org.example.chatmessage.domain.model.ChatMessage;
import org.example.chatroom.domain.model.ChatRoom;

import java.util.Comparator;

public final class ChatRoomComparators {
    private ChatRoomComparators() {}

    public static final Comparator<ChatRoom> BY_POPULARITY_DESC_THEN_ID_DESC = Comparator.comparingDouble(ChatRoom::getPopularity).reversed().thenComparing(ChatRoom::getId, Comparator.reverseOrder());
}
