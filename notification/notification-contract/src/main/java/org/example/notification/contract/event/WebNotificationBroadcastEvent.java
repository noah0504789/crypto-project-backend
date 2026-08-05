package org.example.notification.contract.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.common.enums.KafkaTopic;
import org.example.common.outbox.domain.OutboxDispatchType;
import org.example.common.outbox.domain.OutboxDomainType;
import org.example.common.outbox.domain.event.AbstractOutboxEvent;

@Getter
@ToString
public class WebNotificationBroadcastEvent extends AbstractOutboxEvent {

    private final WebNotificationPayload payload;
    private final String notificationId;

    @JsonCreator
    public WebNotificationBroadcastEvent(
            @JsonProperty("payload") WebNotificationPayload payload,
            @JsonProperty("notificationId") String notificationId,
            @JsonProperty("partitionKey") String partitionKey
    ) {
        super(KafkaTopic.WEB_NOTIFICATION_BROADCAST.getTopicName(), notificationId, partitionKey);
        this.payload = payload;
        this.notificationId = notificationId;
    }

    public static WebNotificationBroadcastEvent of(
            WebNotificationPayload payload,
            String notificationId,
            String partitionKey
    ) {
        return new WebNotificationBroadcastEvent(payload, notificationId, partitionKey);
    }

    @Override
    public OutboxDispatchType getDispatchType() {
        return OutboxDispatchType.BROADCAST;
    }

    @Override
    public OutboxDomainType getDomainType() {
        return OutboxDomainType.NOTIFICATION;
    }
}
