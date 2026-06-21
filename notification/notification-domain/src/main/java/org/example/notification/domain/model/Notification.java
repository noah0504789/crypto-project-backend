package org.example.notification.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.common.event.TypedPayload;
import org.example.common.time.ServiceZoneUtils;
import org.example.notification.contract.event.WebNotificationEvent;
import org.example.notification.contract.event.WebNotificationPayload;
import org.example.notification.domain.event.NotificationEventList;
import org.example.notification.domain.event.NotificationRecipientPayload;
import org.example.notification.domain.event.NotificationSaveEvent;
import org.example.notification.domain.event.NotificationPayload;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class Notification {

    private String id;

    private NotificationType type;
    private String title;
    private String message;
    private List<NotificationMessagePart> messageParts;
    private String link;
    private Map<String, Object> payload;

    private boolean deleted;
    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;

    private NotificationEventList eventList;

    public static Notification create(
            NotificationType type,
            String title,
            String message,
            List<NotificationMessagePart> messageParts,
            String link,
            Map<String, Object> payload,
            LocalDateTime createdAt
    ) {
        return Notification.builder()
                .type(type)
                .title(title)
                .message(message)
                .messageParts(messageParts == null ? List.of() : List.copyOf(messageParts))
                .link(link)
                .payload(payload == null ? Map.of() : Map.copyOf(payload))
                .deleted(false)
                .deletedAt(null)
                .createdAt(createdAt)
                .eventList(new NotificationEventList())
                .build();
    }

    public static Notification rehydrate(
            String id,
            NotificationType type,
            String title,
            String message,
            List<NotificationMessagePart> messageParts,
            String link,
            Map<String, Object> payload,
            boolean deleted,
            LocalDateTime deletedAt,
            LocalDateTime createdAt
    ) {
        return Notification.builder()
                .id(id)
                .type(type)
                .title(title)
                .message(message)
                .messageParts(messageParts == null ? List.of() : List.copyOf(messageParts))
                .link(link)
                .payload(payload == null ? Map.of() : Map.copyOf(payload))
                .deleted(deleted)
                .deletedAt(deletedAt)
                .createdAt(createdAt)
                .eventList(new NotificationEventList())
                .build();
    }

    public void save(
            TypedPayload typedPayload,
            String routingKey,
            List<NotificationRecipient> recipients
    ) {
        List<NotificationRecipientPayload> recipientPayloads = recipients == null ? List.of() : recipients.stream().map(NotificationRecipientPayload::from).toList();

        this.eventList
                .addEvent(NotificationSaveEvent.from(
                        NotificationPayload.from(this),
                        recipientPayloads
                ))
                .addEvent(WebNotificationEvent.of(
                        this.id,
                        routingKey,
                        createWebNotificationPayload(typedPayload)
                ))
                .publish();
    }

    public long toMillis() {
        if (createdAt == null) {
            return 0L;
        }

        return createdAt
                .atZone(ServiceZoneUtils.ZONE_ID)
                .toInstant()
                .toEpochMilli();
    }

    private WebNotificationPayload createWebNotificationPayload(TypedPayload typedPayload) {
        return WebNotificationPayload.of(
                this.type.name(),
                this.title,
                this.message,
                this.toMillis(),
                null,
                null,
                typedPayload
        );
    }
}