package org.example.notification.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.HandleableEvent;
import org.example.common.outbox.domain.event.AbstractOutboxEvent;
import org.example.notification.domain.event.payload.NotificationPayload;
import org.example.notification.domain.event.payload.NotificationRecipientPayload;
import org.example.notification.domain.model.NotificationRecipient;
import org.example.notification.domain.event.port.in.NotificationEventHandler;

import java.util.List;

@Getter
@ToString
public class NotificationSaveEvent extends AbstractOutboxEvent implements HandleableEvent<NotificationEventHandler> {

    private final NotificationPayload payload;
    private final List<NotificationRecipientPayload> recipients;

    @JsonCreator
    public NotificationSaveEvent(
            @JsonProperty("payload") NotificationPayload payload,
            @JsonProperty("recipients") List<NotificationRecipientPayload> recipients
    ) {
        super(KafkaTopic.NOTIFICATION.getTopicName(), payload.id(), payload.id());
        this.payload = payload;
        this.recipients = recipients == null ? List.of() : List.copyOf(recipients);
    }

    public static NotificationSaveEvent from(NotificationPayload payload, List<NotificationRecipientPayload> recipients) {
        return new NotificationSaveEvent(payload, recipients);
    }

    public List<NotificationRecipient> toRecipients() {
        return recipients.stream()
                .map(NotificationRecipientPayload::toDomain)
                .toList();
    }

    @Override
    public void handle(NotificationEventHandler handler, String txId) {
        handler.handle(this, txId);
    }
}