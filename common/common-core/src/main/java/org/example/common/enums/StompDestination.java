package org.example.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StompDestination {

    CHAT_ROOM_PREFIX("/topic/chat/"),
    CHAT_ROOM_BADGE_QUEUE("/queue/chat/badge"),
    CHAT_ACK_QUEUE("/queue/chat/ack"),

    NOTIFICATION_QUEUE("/queue/notification");

    private final String prefix;

    public String destination() {
        return prefix;
    }

    public String destination(String uri) {
        return prefix + uri;
    }

    /** 접두사에 해당하지 않는 목적지면 null. */
    public String uriOf(String destination) {
        if (destination == null || !destination.startsWith(prefix)) {
            return null;
        }

        String uri = destination.substring(prefix.length());

        return uri.isBlank() ? null : uri;
    }
}
