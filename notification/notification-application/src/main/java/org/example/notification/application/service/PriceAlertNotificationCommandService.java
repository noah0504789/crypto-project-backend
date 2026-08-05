package org.example.notification.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.time.Clock;
import org.example.common.event.TypedPayload;
import org.example.common.inbox.application.service.InboxService;
import org.example.common.inbox.exception.DuplicateInboxException;
import org.example.common.outbox.application.port.out.OutboxEventListPublishPort;
import org.example.common.outbox.exception.TemporaryOutboxPersistenceException;
import org.example.notification.application.event.NotificationEventList;
import org.example.notification.application.event.NotificationSaveEvent;
import org.example.notification.application.event.payload.NotificationPayload;
import org.example.notification.application.event.payload.NotificationRecipientPayload;
import org.example.notification.application.port.in.PriceAlertNotificationCommandUseCase;
import org.example.notification.application.port.out.PriceAlertNotificationIdGeneratorPort;
import org.example.notification.application.port.out.PriceAlertRecipientQueryPort;
import org.example.notification.application.service.command.PriceAlertNotificationCreateCommand;
import org.example.notification.application.exception.NotificationPersistException;
import org.example.notification.contract.event.WebNotificationEvent;
import org.example.notification.contract.event.WebNotificationPayload;
import org.example.notification.domain.model.Notification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    @Override
    @Transactional(
            transactionManager = "transactionManager",
            rollbackFor = DuplicateInboxException.class
    )
    public void create(PriceAlertNotificationCreateCommand command) {
        inboxService.save(command.consumerName(), command.eventId());

        String id = idGeneratorPort.generate();
        LocalDateTime createdAt = clock.nowLocalDateTime();

        Notification notification = Notification.createPriceAlert(
                id,
                command.code(),
                command.changeRate(),
                command.toPayload(),
                createdAt
        );

        List<NotificationRecipientPayload> recipientPayloads = createRecipientPayloads(
                notification.getId(),
                command.code(),
                command.threshold(),
                createdAt
        );

        NotificationEventList eventList = createPriceAlertNotificationEvents(
                notification,
                recipientPayloads,
                command.typedPayload(),
                command.partitionKey()
        );

        publishNotificationEvents(eventList);
    }

    private List<NotificationRecipientPayload> createRecipientPayloads(
            String notificationId,
            String code,
            String threshold,
            LocalDateTime deliveredAt
    ) {
        List<UUID> receiverIds = priceAlertRecipientQueryPort.findReceiverIds(code, threshold);

        return receiverIds.stream()
                .map(receiverId -> NotificationRecipientPayload.of(notificationId, receiverId, deliveredAt))
                .toList();
    }

    private NotificationEventList createPriceAlertNotificationEvents(
            Notification notification,
            List<NotificationRecipientPayload> recipientPayloads,
            TypedPayload typedPayload,
            String partitionKey
    ) {
        NotificationSaveEvent saveEvent = NotificationSaveEvent.from(
                NotificationPayload.from(notification),
                recipientPayloads
        );

        WebNotificationEvent webEvent = WebNotificationEvent.of(
                createWebNotificationPayload(notification, typedPayload),
                notification.getId(),
                partitionKey
        );

        return NotificationEventList.of(saveEvent, webEvent);
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
