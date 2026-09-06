package org.example.notification.application.event;

import org.example.common.outbox.domain.event.AbstractOutboxEvent;
import org.example.common.outbox.domain.event.AbstractOutboxEventList;
import org.example.notification.application.event.payload.NotificationPayload;
import org.example.notification.application.event.payload.NotificationRecipientPayload;
import org.example.notification.contract.event.PriceAlertPayload;
import org.example.notification.contract.event.WebNotificationBroadcastEvent;
import org.example.notification.contract.event.WebNotificationMessagePart;
import org.example.notification.contract.event.WebNotificationPayload;
import org.example.notification.domain.model.Notification;

import java.util.ArrayList;
import java.util.List;

public class NotificationEventList extends AbstractOutboxEventList {

    private NotificationEventList() {
        super();
    }

    public static NotificationEventList of(AbstractOutboxEvent... events) {
        return AbstractOutboxEventList.of(NotificationEventList::new, events);
    }

    /** 영속 이벤트는 수신자 전체를 담아 1건, 전달 이벤트는 게이트웨이가 수신자별로 라우팅하도록 수신자마다 1건이다. */
    public static NotificationEventList forPriceAlert(
            Notification notification,
            List<NotificationRecipientPayload> recipientPayloads,
            PriceAlertPayload priceAlertPayload
    ) {
        WebNotificationPayload webNotificationPayload = toWebNotificationPayload(notification, priceAlertPayload);

        List<AbstractOutboxEvent> events = new ArrayList<>();
        events.add(NotificationSaveEvent.from(NotificationPayload.from(notification), recipientPayloads));
        recipientPayloads.stream()
                .map(NotificationRecipientPayload::receiverId)
                .map(receiverId -> WebNotificationBroadcastEvent.of(
                        webNotificationPayload,
                        notification.getId(),
                        receiverId.toString()
                ))
                .forEach(events::add);

        return of(events.toArray(AbstractOutboxEvent[]::new));
    }

    private static WebNotificationPayload toWebNotificationPayload(
            Notification notification,
            PriceAlertPayload priceAlertPayload
    ) {
        return WebNotificationPayload.withoutLink(
                notification.getType().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getCreatedAtMs(),
                notification.getMessageParts().stream()
                        .map(part -> new WebNotificationMessagePart(part.text(), part.bold(), part.lineBreakAfter()))
                        .toList(),
                priceAlertPayload
        );
    }
}
