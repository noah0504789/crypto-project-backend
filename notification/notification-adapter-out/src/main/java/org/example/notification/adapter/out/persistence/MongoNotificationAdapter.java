package org.example.notification.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.example.common.clock.Clock;
import org.example.notification.application.port.out.NotificationPersistencePort;
import org.example.notification.domain.model.Notification;
import org.example.notification.application.service.query.NotificationInboxItem;
import org.example.notification.domain.model.NotificationRecipient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MongoNotificationAdapter implements NotificationPersistencePort {

    private final MongoNotificationRepository notificationRepository;
    private final MongoNotificationRecipientRepository notificationRecipientRepository;
    private final Clock clock;

    @Override
    public Notification save(Notification notification) {
        MongoNotification saved = notificationRepository.save(
                MongoNotification.fromDomain(notification)
        );

        return saved.toDomain();
    }

    @Override
    public void saveRecipients(List<NotificationRecipient> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            return;
        }

        List<MongoNotificationRecipient> documents = recipients.stream()
                .map(MongoNotificationRecipient::fromDomain)
                .toList();

        notificationRecipientRepository.saveAllBulk(documents);
    }

    @Override
    public List<NotificationInboxItem> listLatest(UUID receiverId, int limit) {
        if (receiverId == null || limit <= 0) {
            return List.of();
        }

        List<MongoNotificationRecipient> recipients = notificationRecipientRepository.listLatest(receiverId, limit);

        return findInboxItemsByRecipients(recipients);
    }

    @Override
    public List<NotificationInboxItem> listPrev(
            UUID receiverId,
            String lastRecipientId,
            Long lastDeliveredAtMillis,
            int limit
    ) {
        if (receiverId == null
                || lastRecipientId == null
                || lastRecipientId.isBlank()
                || !ObjectId.isValid(lastRecipientId)
                || lastDeliveredAtMillis == null
                || limit <= 0
        ) {
            return List.of();
        }

        ObjectId lastRecipientObjectId = new ObjectId(lastRecipientId);
        Instant lastDeliveredAt = Instant.ofEpochMilli(lastDeliveredAtMillis);

        List<MongoNotificationRecipient> recipients = notificationRecipientRepository.listPrev(receiverId, lastRecipientObjectId, lastDeliveredAt, limit);

        return findInboxItemsByRecipients(recipients);
    }

    @Override
    public boolean markAsRead(String notificationId, UUID receiverId) {
        if (notificationId == null
                || notificationId.isBlank()
                || !ObjectId.isValid(notificationId)
                || receiverId == null
        ) {
            return false;
        }

        long modifiedCount = notificationRecipientRepository.markAsRead(new ObjectId(notificationId), receiverId, clock.now());

        return modifiedCount > 0;
    }

    private List<NotificationInboxItem> findInboxItemsByRecipients(List<MongoNotificationRecipient> mongoRecipients) {
        if (mongoRecipients == null || mongoRecipients.isEmpty()) {
            return List.of();
        }

        Set<ObjectId> notificationIds = mongoRecipients.stream()
                .map(MongoNotificationRecipient::getNotificationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (notificationIds.isEmpty()) {
            return List.of();
        }

        List<NotificationRecipient> recipients = mongoRecipients.stream()
                .map(MongoNotificationRecipient::toDomain)
                .toList();

        Map<String, Notification> notificationMap =
                notificationRepository.findByIdInAndDeletedFalse(notificationIds)
                        .stream()
                        .map(MongoNotification::toDomain)
                        .collect(Collectors.toMap(
                                Notification::getId,
                                Function.identity()
                        ));

        return recipients.stream()
                .map(recipient -> toInboxItem(recipient, notificationMap))
                .filter(Objects::nonNull)
                .toList();
    }

    private NotificationInboxItem toInboxItem(NotificationRecipient recipient, Map<String, Notification> notificationMap) {
        Notification notification = notificationMap.get(recipient.getNotificationId());

        if (notification == null) {
            return null;
        }

        return NotificationInboxItem.of(notification, recipient);
    }
}