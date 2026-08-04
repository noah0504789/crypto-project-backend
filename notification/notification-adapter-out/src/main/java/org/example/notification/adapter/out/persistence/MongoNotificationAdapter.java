package org.example.notification.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.example.common.time.Clock;
import org.example.notification.application.port.out.NotificationPersistencePort;
import org.example.notification.domain.model.Notification;
import org.example.notification.application.service.result.NotificationInboxItem;
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
    public List<NotificationInboxItem> listLatestInboxItems(UUID receiverId, int limit) {
        if (receiverId == null || limit <= 0) {
            return List.of();
        }

        List<MongoNotificationRecipient> recipients = notificationRecipientRepository.listLatest(receiverId, limit);

        return findInboxItemsByRecipientsFromPrimary(recipients);
    }

    @Override
    public List<NotificationInboxItem> listInboxItemsBefore(
            UUID receiverId,
            String lastRecipientId,
            Long lastDeliveredAtMs,
            int limit
    ) {
        if (receiverId == null
                || lastRecipientId == null
                || lastRecipientId.isBlank()
                || !ObjectId.isValid(lastRecipientId)
                || lastDeliveredAtMs == null
                || limit <= 0
        ) {
            return List.of();
        }

        ObjectId lastRecipientObjectId = new ObjectId(lastRecipientId);
        Instant lastDeliveredAt = Instant.ofEpochMilli(lastDeliveredAtMs);

        List<MongoNotificationRecipient> recipients = notificationRecipientRepository.listHistoricalBefore(receiverId, lastRecipientObjectId, lastDeliveredAt, limit);

        return findInboxItemsByRecipientsFromSecondary(recipients);
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

    @Override
    public List<NotificationRecipient> listLatestRecipients(UUID receiverId, int limit) {
        if (receiverId == null || limit <= 0) {
            return List.of();
        }

        return toDomainRecipients(notificationRecipientRepository.listLatest(receiverId, limit));
    }

    @Override
    public List<NotificationRecipient> listRecipientsBefore(
            UUID receiverId,
            String lastRecipientId,
            Long lastDeliveredAtMs,
            int limit
    ) {
        if (receiverId == null
                || lastRecipientId == null
                || lastRecipientId.isBlank()
                || !ObjectId.isValid(lastRecipientId)
                || lastDeliveredAtMs == null
                || limit <= 0
        ) {
            return List.of();
        }

        ObjectId lastRecipientObjectId = new ObjectId(lastRecipientId);
        Instant lastDeliveredAt = Instant.ofEpochMilli(lastDeliveredAtMs);

        return toDomainRecipients(
                notificationRecipientRepository.listHistoricalBefore(receiverId, lastRecipientObjectId, lastDeliveredAt, limit)
        );
    }

    @Override
    public List<Notification> findByIds(Set<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        Set<ObjectId> objectIds = ids.stream()
                .filter(Objects::nonNull)
                .filter(ObjectId::isValid)
                .map(ObjectId::new)
                .collect(Collectors.toSet());

        if (objectIds.isEmpty()) {
            return List.of();
        }

        return notificationRepository.findByIdInAndDeletedFalse(objectIds)
                .stream()
                .map(MongoNotification::toDomain)
                .toList();
    }

    private List<NotificationRecipient> toDomainRecipients(List<MongoNotificationRecipient> mongoRecipients) {
        if (mongoRecipients == null || mongoRecipients.isEmpty()) {
            return List.of();
        }

        return mongoRecipients.stream()
                .map(MongoNotificationRecipient::toDomain)
                .toList();
    }

    private List<NotificationInboxItem> findInboxItemsByRecipientsFromPrimary(List<MongoNotificationRecipient> mongoRecipients) {
        return findInboxItemsByRecipients(mongoRecipients, notificationRepository::findByIdInAndDeletedFalse);
    }

    private List<NotificationInboxItem> findInboxItemsByRecipientsFromSecondary(List<MongoNotificationRecipient> mongoRecipients) {
        return findInboxItemsByRecipients(mongoRecipients, notificationRepository::findByIdInAndDeletedFalseFromSecondary);
    }

    private List<NotificationInboxItem> findInboxItemsByRecipients(
            List<MongoNotificationRecipient> mongoRecipients,
            Function<Set<ObjectId>,
            List<MongoNotification>> notificationFinder
    ) {
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

        Map<String, Notification> notificationMap = notificationFinder.apply(notificationIds)
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