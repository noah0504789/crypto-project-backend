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

    NOTIFICATION("notification-event", "notification-event.dlq", null),
    WEB_NOTIFICATION_BROADCAST("web-notification-broadcast-event", "web-notification-broadcast-event.dlq", "web-notification-broadcast-event-out"),

    MARKET_CHANGED_BROADCAST("market-broadcast-event", null, null),
    PRICE_ALERT_DETECTED(null, null, "price-alert-detected-event-out");

    private final String topicName;
    private final String dlqTopicName;
    private final String bindingName;
}
