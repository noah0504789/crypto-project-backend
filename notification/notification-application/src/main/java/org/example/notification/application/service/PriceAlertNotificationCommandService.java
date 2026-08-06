package org.example.notification.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.time.Clock;
import org.example.common.event.TypedPayload;
import org.example.common.inbox.application.service.InboxService;
import org.example.common.inbox.exception.DuplicateInboxException;
import org.example.common.outbox.application.port.out.OutboxEventListPublishPort;
import org.example.common.outbox.domain.event.AbstractOutboxEvent;
import org.example.common.outbox.exception.TemporaryOutboxPersistenceException;
import org.example.notification.application.event.NotificationEventList;
import org.example.notification.application.event.NotificationSaveEvent;
import org.example.notification.application.event.payload.NotificationPayload;
import org.example.notification.application.event.payload.NotificationRecipientPayload;
import org.example.notification.application.port.in.PriceAlertNotificationCommandUseCase;
import org.example.notification.application.port.out.PriceAlertNotificationIdGeneratorPort;
import org.example.notification.application.port.out.PriceAlertRecipientQueryPort;
import org.example.notification.application.service.command.PriceAlertNotificationCreateCommand;
import org.example.notification.application.service.properties.PriceAlertNotificationProperties;
import org.example.notification.application.exception.NotificationPersistException;
import org.example.notification.contract.event.WebNotificationBroadcastEvent;
import org.example.notification.contract.event.WebNotificationPayload;
import org.example.notification.domain.model.Notification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceAlertNotificationCommandService implements PriceAlertNotificationCommandUseCase {

    private final Clock clock;
    private final PriceAlertNotificationIdGeneratorPort idGeneratorPort;
    private final PriceAlertRecipientQueryPort priceAlertRecipientQueryPort;
    private final OutboxEventListPublishPort outboxEventListPublishPort;
    private final InboxService inboxService;
    private final PriceAlertNotificationProperties properties;

    @Override
    @Transactional(
            transactionManager = "transactionManager",
            rollbackFor = DuplicateInboxException.class
    )
    public void create(PriceAlertNotificationCreateCommand command) {
        inboxService.save(command.consumerName(), command.eventId());

        if (isStale(command.occurredAtMs())) {
            log.info(
                    "Stale price alert event skipped. eventId={}, occurredAtMs={}, maxEventAge={}",
                    command.eventId(), command.occurredAtMs(), properties.maxEventAge()
            );
            return;
        }

        List<UUID> receiverIds = priceAlertRecipientQueryPort.findReceiverIds(
                command.code(),
                command.threshold()
        );
        if (receiverIds.isEmpty()) {
            log.debug("Price alert event skipped because no recipients were found. eventId={}", command.eventId());
            return;
        }

        String id = idGeneratorPort.generate();
        LocalDateTime createdAt = clock.nowLocalDateTime();

        Notification notification = Notification.createPriceAlert(
                id,
                command.code(),
                command.price(),
                command.avgPrice(),
                command.avgInterval(),
                command.changeRate(),
                command.toPayload(),
                createdAt
        );

        List<NotificationRecipientPayload> recipientPayloads = createRecipientPayloads(
                notification.getId(),
                receiverIds,
                createdAt
        );

        NotificationEventList eventList = createPriceAlertNotificationEvents(
                notification,
                recipientPayloads,
                command.typedPayload()
        );

        publishNotificationEvents(eventList);
    }

    private boolean isStale(Long occurredAtMs) {
        if (occurredAtMs == null) {
            return false;
        }

        long staleBoundaryMs = clock.nowMs() - properties.maxEventAge().toMillis();
        return occurredAtMs < staleBoundaryMs;
    }

    private List<NotificationRecipientPayload> createRecipientPayloads(
            String notificationId,
            List<UUID> receiverIds,
            LocalDateTime deliveredAt
    ) {
        return receiverIds.stream()
                .map(receiverId -> NotificationRecipientPayload.of(
                        idGeneratorPort.generate(), notificationId, receiverId, deliveredAt))
                .toList();
    }

    private NotificationEventList createPriceAlertNotificationEvents(
            Notification notification,
            List<NotificationRecipientPayload> recipientPayloads,
            TypedPayload typedPayload
    ) {
        NotificationSaveEvent saveEvent = NotificationSaveEvent.from(
                NotificationPayload.from(notification),
                recipientPayloads
        );

        WebNotificationPayload webNotificationPayload = createWebNotificationPayload(notification, typedPayload);

        List<AbstractOutboxEvent> events = new ArrayList<>();
        events.add(saveEvent);
        recipientPayloads.stream()
                .map(NotificationRecipientPayload::receiverId)
                .map(receiverId -> WebNotificationBroadcastEvent.of(
                        webNotificationPayload,
                        notification.getId(),
                        receiverId.toString()
                ))
                .forEach(events::add);

        return NotificationEventList.of(events.toArray(AbstractOutboxEvent[]::new));
    }

    private WebNotificationPayload createWebNotificationPayload(Notification notification, TypedPayload typedPayload) {
        return WebNotificationPayload.withoutLink(
                notification.getType().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getCreatedAtMs(),
                typedPayload
        );
    }

    private void publishNotificationEvents(NotificationEventList eventList) {
        try {
            outboxEventListPublishPort.publish(eventList);
        } catch (TemporaryOutboxPersistenceException e) {
            throw e;
        } catch (Exception e) {
            throw new NotificationPersistException(
                    "failed to publish notification events",
                    e
            );
        }
    }
}
