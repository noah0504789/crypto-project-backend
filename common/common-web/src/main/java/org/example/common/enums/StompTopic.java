package org.example.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StompTopic {

    CHAT_ROOM("/topic/chat/"),
    CHAT_ROOM_BADGE("/queue/chat/badge"),

    NOTIFICATION("/topic/notification/");

    private final String prefix;

    public String destination(String uri) {
        return prefix + uri;
    }
}
