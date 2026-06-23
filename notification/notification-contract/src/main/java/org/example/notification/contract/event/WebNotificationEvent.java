package org.example.notification.contract.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.common.enums.KafkaTopic;
import org.example.common.outbox.domain.event.AbstractOutboxEvent;

@Getter
@ToString
public class WebNotificationEvent extends AbstractOutboxEvent {

    private final WebNotificationPayload payload;
    private final String notificationId;
    private final String routingKey;

    @JsonCreator
    public WebNotificationEvent(
            @JsonProperty("payload") WebNotificationPayload payload,
            @JsonProperty("notificationId") String notificationId,
            @JsonProperty("routingKey") String routingKey
    ) {
        super(KafkaTopic.WEB_NOTIFICATION_BROADCAST.getTopicName(), notificationId, routingKey);
        this.payload = payload;
        this.notificationId = notificationId;
        this.routingKey = routingKey;
    }

    public static WebNotificationEvent of(
            String notificationId,
            String routingKey,
            WebNotificationPayload payload
    ) {
        return new WebNotificationEvent(payload, notificationId, routingKey);
    }
}