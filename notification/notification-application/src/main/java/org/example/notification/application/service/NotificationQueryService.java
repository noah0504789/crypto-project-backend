package org.example.notification.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.notification.application.port.in.NotificationQueryUseCase;
import org.example.notification.application.port.out.NotificationCachePort;
import org.example.notification.application.port.out.NotificationPersistencePort;
import org.example.notification.application.service.query.ListNotificationInboxItemsQuery;
import org.example.notification.application.service.result.NotificationInboxItem;
import org.example.notification.domain.model.Notification;
import org.example.notification.domain.model.NotificationRecipient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * inbox 조회 = "정렬/커서(인덱스)"(Mongo recipient) + "알림 정보"(Redis 1차 캐시) 조합.
 *
 * <p>알림 정보는 불변이라 캐시 hit 은 항상 정답이다. 캐시에 없는 id 만 Mongo 에서 재조회하고 warm-up 한다.
 * cache stampede 는 별도 장치(PER/락) 없이 <b>생성 시 선적재 + 긴 TTL + LFU 축출</b>로 완화한다
 * (miss 자체가 드물고, 겹쳐도 싼 포인트 조회 + fail-open 이라 무해).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationQueryService implements NotificationQueryUseCase {

    private final NotificationCachePort cache;
    private final NotificationPersistencePort persistence;

    @Override
    public List<NotificationInboxItem> listInboxItems(ListNotificationInboxItemsQuery query) {
        List<NotificationRecipient> recipients = loadRecipients(query);

        if (recipients.isEmpty()) {
            return List.of();
        }

        Map<String, Notification> notifications = resolveNotifications(orderedNotificationIds(recipients));

        return recipients.stream()
                .map(recipient -> toInboxItem(recipient, notifications))
                .filter(Objects::nonNull)
                .toList();
    }

    private List<NotificationRecipient> loadRecipients(ListNotificationInboxItemsQuery query) {
        int limit = query.limit();

        return query.hasNoCursor()
                ? persistence.listLatestRecipients(query.receiverId(), limit)
                : persistence.listRecipientsBefore(query.receiverId(), query.lastRecipientId(), query.cursorDeliveredAtMs(), limit);
    }

    private Set<String> orderedNotificationIds(List<NotificationRecipient> recipients) {
        return recipients.stream()
                .map(NotificationRecipient::getNotificationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 캐시 우선으로 알림 정보를 해석한다. hit 은 그대로 쓰고(불변), 없는 id 만 DB 에서 재조회 후 warm-up 한다.
     */
    private Map<String, Notification> resolveNotifications(Set<String> notificationIds) {
        Map<String, Notification> notifications = new LinkedHashMap<>(cache.findByIds(notificationIds));

        Set<String> misses = notificationIds.stream()
                .filter(id -> !notifications.containsKey(id))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (!misses.isEmpty()) {
            List<Notification> reloaded = persistence.findByIds(misses);
            reloaded.forEach(notification -> notifications.put(notification.getId(), notification));
            warmUpSafely(reloaded);
        }

        return notifications;
    }

    private void warmUpSafely(List<Notification> reloaded) {
        if (reloaded.isEmpty()) {
            return;
        }

        try {
            cache.warmUpAll(reloaded);
        } catch (RuntimeException e) {
            log.warn("[cache] notification warmUp failed. size={}", reloaded.size(), e);
        }
    }

    private NotificationInboxItem toInboxItem(NotificationRecipient recipient, Map<String, Notification> notifications) {
        Notification notification = notifications.get(recipient.getNotificationId());

        if (notification == null) {
            return null;
        }

        return NotificationInboxItem.of(notification, recipient);
    }
}
