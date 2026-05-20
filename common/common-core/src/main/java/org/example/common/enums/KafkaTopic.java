package org.example.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum KafkaTopic {

    CHAT_ROOM("chatroom-event", "chatroom-event.dlq", null),
    CHAT_ROOM_BROADCAST("chatroom-broadcast-event", "chatroom-broadcast-event.dlq", null),
    CHAT_MESSAGE("chatmessage-event", "chatmessage-event.dlq", null),
    CHAT_MESSAGE_BROADCAST("chatmessage-broadcast-event", "chatmessage-broadcast-event.dlq", null),
    NOTIFICATION_BROADCAST("notification-broadcast-event", "notification-broadcast-event.dlq", null),
    NOTIFICATION_WEB(null, null, "web-notification-event-out");

    private final String topicName;
    private final String dlqTopicName;
    private final String bindingName;
}
