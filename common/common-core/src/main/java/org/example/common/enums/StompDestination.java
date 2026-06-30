package org.example.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StompDestination {

    CHAT_ROOM_PREFIX("/topic/chat/"),
    CHAT_ROOM_BADGE_QUEUE("/queue/chat/badge"),
    CHAT_ACK_QUEUE("/queue/chat/ack"),

    NOTIFICATION_PREFIX("/topic/notification/");

    private final String prefix;

    public String destination() {
        return prefix;
    }

    public String destination(String uri) {
        return prefix + uri;
    }
}