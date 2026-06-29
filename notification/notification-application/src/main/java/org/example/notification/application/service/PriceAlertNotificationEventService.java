package org.example.notification.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.clock.Clock;
import org.example.common.outbox.application.port.out.OutboxEventListPublishPort;
import org.example.common.outbox.exception.TemporaryOutboxPersistenceException;
import org.example.notification.application.port.out.PriceAlertRecipientQueryPort;
import org.example.notification.application.service.command.PriceAlertNotificationCreateCommand;
import org.example.notification.common.exception.NotificationPersistException;
import org.example.notification.domain.model.Notification;
import org.example.notification.domain.model.NotificationMessagePart;
import org.example.notification.domain.model.NotificationRecipient;
import org.example.notification.domain.model.NotificationType;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceAlertNotificationEventService {

    private final Clock clock;
    private final PriceAlertRecipientQueryPort priceAlertRecipientQueryPort;
    private final OutboxEventListPublishPort outboxEventListPublishPort;

    public void create(PriceAlertNotificationCreateCommand command) {
        LocalDateTime deliveredAt = clock.nowLocalDateTime();

        Notification notification = createNotification(command, deliveredAt);

        List<UUID> receiverIds = priceAlertRecipientQueryPort.findReceiverIds(command.code(), command.threshold());

        List<NotificationRecipient> recipients = receiverIds.stream()
                .map(receiverId -> NotificationRecipient.create(notification.getId(), receiverId, deliveredAt))
                .toList();

        notification.save(command.typedPayload(), command.routingKey(), recipients);

        publishNotificationEvent(notification);
    }

    private Notification createNotification(PriceAlertNotificationCreateCommand command, LocalDateTime deliveredAt) {
        return Notification.createPriceAlert(
                command.code(),
                command.changeRate(),
                command.toPayload(),
                deliveredAt
        );
    }

    private void publishNotificationEvent(Notification notification) {
        try {
            outboxEventListPublishPort.publish(notification.pullEventList());
        } catch (TemporaryOutboxPersistenceException e) {
            throw e;
        } catch (Exception e) {
            throw new NotificationPersistException("failed to publish notification event", e);
        }
    }
}