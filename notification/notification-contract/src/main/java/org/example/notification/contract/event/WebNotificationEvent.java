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

    @JsonCreator
    public WebNotificationEvent(
            @JsonProperty("payload") WebNotificationPayload payload,
            @JsonProperty("notificationId") String notificationId,
            @JsonProperty("partitionKey") String partitionKey
    ) {
        super(KafkaTopic.WEB_NOTIFICATION_BROADCAST.getTopicName(), notificationId, partitionKey);
        this.payload = payload;
        this.notificationId = notificationId;
    }

    public static WebNotificationEvent of(
            WebNotificationPayload payload,
            String notificationId,
            String partitionKey
    ) {
        return new WebNotificationEvent(payload, notificationId, partitionKey);
    }
}