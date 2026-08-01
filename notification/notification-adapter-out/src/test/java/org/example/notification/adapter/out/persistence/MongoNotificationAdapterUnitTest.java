package org.example.notification.adapter.out.persistence;

import org.bson.types.ObjectId;
import org.example.common.clock.Clock;
import org.example.common.time.ServiceZoneUtils;
import org.example.notification.application.service.result.NotificationInboxItem;
import org.example.notification.domain.model.Notification;
import org.example.notification.domain.model.NotificationRecipient;
import org.example.notification.domain.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MongoNotificationAdapterUnitTest {

    @Mock
    private MongoNotificationRepository notificationRepository;

    @Mock
    private MongoNotificationRecipientRepository notificationRecipientRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private MongoNotificationAdapter sut;

    private final UUID receiverId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ObjectId notificationId1 = new ObjectId("65f000000000000000000001");
    private final ObjectId notificationId2 = new ObjectId("65f000000000000000000002");

    private final ObjectId recipientId1 = new ObjectId("66f000000000000000000001");
    private final ObjectId recipientId2 = new ObjectId("66f000000000000000000002");

    private final LocalDateTime createdAt1 = LocalDateTime.of(2026, 6, 23, 10, 0);
    private final LocalDateTime createdAt2 = LocalDateTime.of(2026, 6, 23, 11, 0);

    private final LocalDateTime deliveredAt1 = LocalDateTime.of(2026, 6, 23, 10, 1);
    private final LocalDateTime deliveredAt2 = LocalDateTime.of(2026, 6, 23, 11, 1);

    @Nested
    @DisplayName("save")
    class SaveTest {

        @Test
        @DisplayName("알림 원본을 저장하고 저장된 Notification 도메인을 반환한다")
        void save() {
            Notification notification = notification(
                    notificationId1.toHexString(),
                    "가격 알림",
                    "KRW-BTC이 7.0% 이상 상승했습니다.",
                    createdAt1
            );

            MongoNotification saved = MongoNotification.fromDomain(notification);

            when(notificationRepository.save(any(MongoNotification.class)))
                    .thenReturn(saved);

            Notification result = sut.save(notification);

            assertEquals(notificationId1.toHexString(), result.getId());
            assertEquals(NotificationType.PRICE_ALERT, result.getType());
            assertEquals("가격 알림", result.getTitle());
            assertEquals("KRW-BTC이 7.0% 이상 상승했습니다.", result.getMessage());
            assertEquals(createdAt1, result.getCreatedAt());

            verify(notificationRepository).save(any(MongoNotification.class));
            verifyNoInteractions(notificationRecipientRepository);
        }
    }

    @Nested
    @DisplayName("saveRecipients")
    class SaveRecipientsTest {

        @Test
        @DisplayName("수신자 알림 목록을 벌크 저장한다")
        void saveRecipients() {
            MongoNotificationRecipient recipient1 = mongoRecipient(
                    recipientId1,
                    notificationId1,
                    false,
                    deliveredAt1
            );

            MongoNotificationRecipient recipient2 = mongoRecipient(
                    recipientId2,
                    notificationId2,
                    true,
                    deliveredAt2
            );

            NotificationRecipient domainRecipient1 = recipient1.toDomain();
            NotificationRecipient domainRecipient2 = recipient2.toDomain();

            sut.saveRecipients(List.of(domainRecipient1, domainRecipient2));

            verify(notificationRecipientRepository).saveAllBulk(argThat(documents ->
                    documents.size() == 2
                            && documents.get(0).getNotificationId().equals(notificationId1)
                            && documents.get(1).getNotificationId().equals(notificationId2)
            ));
        }

        @Test
        @DisplayName("수신자 알림 목록이 null이면 벌크 저장하지 않는다")
        void saveRecipientsWhenNull() {
            sut.saveRecipients(null);

            verifyNoInteractions(notificationRecipientRepository);
        }

        @Test
        @DisplayName("수신자 알림 목록이 비어 있으면 벌크 저장하지 않는다")
        void saveRecipientsWhenEmpty() {
            sut.saveRecipients(List.of());

            verifyNoInteractions(notificationRecipientRepository);
        }
    }

    @Nested
    @DisplayName("listLatestInboxItems")
    class ListLatestInboxItemsTest {

        @Test
        @DisplayName("최신 알림함 목록은 primary 조회로 Recipient와 Notification 원본을 조합해서 반환한다")
        void listLatestInboxItems() {
            MongoNotificationRecipient recipient2 = mongoRecipient(
                    recipientId2,
                    notificationId2,
                    true,
                    deliveredAt2
            );

            MongoNotificationRecipient recipient1 = mongoRecipient(
                    recipientId1,
                    notificationId1,
                    false,
                    deliveredAt1
            );

            MongoNotification notification1 = mongoNotification(
                    notificationId1,
                    "가격 알림 1",
                    "KRW-BTC 상승",
                    createdAt1
            );

            MongoNotification notification2 = mongoNotification(
                    notificationId2,
                    "가격 알림 2",
                    "KRW-ETH 하락",
                    createdAt2
            );

            when(notificationRecipientRepository.listLatest(receiverId, 2))
                    .thenReturn(List.of(recipient2, recipient1));

            when(notificationRepository.findByIdInAndDeletedFalse(Set.of(notificationId1, notificationId2)))
                    .thenReturn(List.of(notification1, notification2));

            List<NotificationInboxItem> result = sut.listLatestInboxItems(receiverId, 2);

            assertEquals(2, result.size());

            assertEquals(notificationId2.toHexString(), result.get(0).notificationId());
            assertEquals(recipientId2.toHexString(), result.get(0).recipientId());
            assertEquals("가격 알림 2", result.get(0).title());
            assertTrue(result.get(0).read());
            assertEquals(deliveredAt2, result.get(0).deliveredAt());

            assertEquals(notificationId1.toHexString(), result.get(1).notificationId());
            assertEquals(recipientId1.toHexString(), result.get(1).recipientId());
            assertEquals("가격 알림 1", result.get(1).title());
            assertFalse(result.get(1).read());
            assertEquals(deliveredAt1, result.get(1).deliveredAt());

            verify(notificationRecipientRepository).listLatest(receiverId, 2);
            verify(notificationRepository).findByIdInAndDeletedFalse(Set.of(notificationId1, notificationId2));
            verify(notificationRepository, never()).findByIdInAndDeletedFalseFromSecondary(anySet());
        }

        @Test
        @DisplayName("receiverId가 null이면 빈 리스트를 반환한다")
        void listLatestInboxItemsWhenReceiverIdIsNull() {
            List<NotificationInboxItem> result = sut.listLatestInboxItems(null, 10);

            assertTrue(result.isEmpty());

            verifyNoInteractions(notificationRecipientRepository);
            verifyNoInteractions(notificationRepository);
        }

        @Test
        @DisplayName("limit이 0 이하이면 빈 리스트를 반환한다")
        void listLatestInboxItemsWhenLimitIsInvalid() {
            List<NotificationInboxItem> result = sut.listLatestInboxItems(receiverId, 0);

            assertTrue(result.isEmpty());

            verifyNoInteractions(notificationRecipientRepository);
            verifyNoInteractions(notificationRepository);
        }

        @Test
        @DisplayName("Notification 원본이 삭제되었거나 조회되지 않으면 InboxItem 조립에서 제외한다")
        void listLatestInboxItemsSkipMissingNotification() {
            MongoNotificationRecipient recipient2 = mongoRecipient(
                    recipientId2,
                    notificationId2,
                    false,
                    deliveredAt2
            );

            MongoNotificationRecipient recipient1 = mongoRecipient(
                    recipientId1,
                    notificationId1,
                    false,
                    deliveredAt1
            );

            MongoNotification notification1 = mongoNotification(
                    notificationId1,
                    "가격 알림 1",
                    "KRW-BTC 상승",
                    createdAt1
            );

            when(notificationRecipientRepository.listLatest(receiverId, 2))
                    .thenReturn(List.of(recipient2, recipient1));

            when(notificationRepository.findByIdInAndDeletedFalse(Set.of(notificationId1, notificationId2)))
                    .thenReturn(List.of(notification1));

            List<NotificationInboxItem> result = sut.listLatestInboxItems(receiverId, 2);

            assertEquals(1, result.size());
            assertEquals(notificationId1.toHexString(), result.get(0).notificationId());
            assertEquals(recipientId1.toHexString(), result.get(0).recipientId());

            verify(notificationRecipientRepository).listLatest(receiverId, 2);
            verify(notificationRepository).findByIdInAndDeletedFalse(Set.of(notificationId1, notificationId2));
            verify(notificationRepository, never()).findByIdInAndDeletedFalseFromSecondary(anySet());
        }
    }

    @Nested
    @DisplayName("listInboxItemsBefore")
    class ListInboxItemsBeforeTest {

        @Test
        @DisplayName("이전 알림함 목록은 secondary 조회로 Recipient와 Notification 원본을 조합해서 반환한다")
        void listInboxItemsBefore() {
            MongoNotificationRecipient recipient1 = mongoRecipient(
                    recipientId1,
                    notificationId1,
                    false,
                    deliveredAt1
            );

            MongoNotification notification1 = mongoNotification(
                    notificationId1,
                    "가격 알림 1",
                    "KRW-BTC 상승",
                    createdAt1
            );

            long lastDeliveredAtMillis = getCreatedAtMs(deliveredAt2);
            Instant lastDeliveredAt = Instant.ofEpochMilli(lastDeliveredAtMillis);

            when(notificationRecipientRepository.listHistoricalBefore(receiverId, recipientId2, lastDeliveredAt, 1))
                    .thenReturn(List.of(recipient1));

            when(notificationRepository.findByIdInAndDeletedFalseFromSecondary(Set.of(notificationId1)))
                    .thenReturn(List.of(notification1));

            List<NotificationInboxItem> result = sut.listInboxItemsBefore(
                    receiverId,
                    recipientId2.toHexString(),
                    lastDeliveredAtMillis,
                    1
            );

            assertEquals(1, result.size());
            assertEquals(notificationId1.toHexString(), result.get(0).notificationId());
            assertEquals(recipientId1.toHexString(), result.get(0).recipientId());
            assertEquals("가격 알림 1", result.get(0).title());
            assertFalse(result.get(0).read());

            verify(notificationRecipientRepository).listHistoricalBefore(receiverId, recipientId2, lastDeliveredAt, 1);
            verify(notificationRepository).findByIdInAndDeletedFalseFromSecondary(Set.of(notificationId1));
            verify(notificationRepository, never()).findByIdInAndDeletedFalse(anySet());
        }

        @Test
        @DisplayName("커서 id가 유효하지 않으면 빈 리스트를 반환한다")
        void listInboxItemsBeforeWhenCursorIdIsInvalid() {
            List<NotificationInboxItem> result = sut.listInboxItemsBefore(
                    receiverId,
                    "invalid-object-id",
                    getCreatedAtMs(deliveredAt1),
                    10
            );

            assertTrue(result.isEmpty());

            verifyNoInteractions(notificationRecipientRepository);
            verifyNoInteractions(notificationRepository);
        }

        @Test
        @DisplayName("deliveredAt 커서가 null이면 빈 리스트를 반환한다")
        void listInboxItemsBeforeWhenDeliveredAtCursorIsNull() {
            List<NotificationInboxItem> result = sut.listInboxItemsBefore(
                    receiverId,
                    recipientId1.toHexString(),
                    null,
                    10
            );

            assertTrue(result.isEmpty());

            verifyNoInteractions(notificationRecipientRepository);
            verifyNoInteractions(notificationRepository);
        }

        @Test
        @DisplayName("Notification 원본이 삭제되었거나 조회되지 않으면 InboxItem 조립에서 제외한다")
        void listInboxItemsBeforeSkipMissingNotification() {
            MongoNotificationRecipient recipient2 = mongoRecipient(
                    recipientId2,
                    notificationId2,
                    false,
                    deliveredAt2
            );

            MongoNotificationRecipient recipient1 = mongoRecipient(
                    recipientId1,
                    notificationId1,
                    false,
                    deliveredAt1
            );

            MongoNotification notification1 = mongoNotification(
                    notificationId1,
                    "가격 알림 1",
                    "KRW-BTC 상승",
                    createdAt1
            );

            long lastDeliveredAtMillis = getCreatedAtMs(deliveredAt2.plusMinutes(1));
            Instant lastDeliveredAt = Instant.ofEpochMilli(lastDeliveredAtMillis);

            when(notificationRecipientRepository.listHistoricalBefore(receiverId, recipientId2, lastDeliveredAt, 2))
                    .thenReturn(List.of(recipient2, recipient1));

            when(notificationRepository.findByIdInAndDeletedFalseFromSecondary(Set.of(notificationId1, notificationId2)))
                    .thenReturn(List.of(notification1));

            List<NotificationInboxItem> result = sut.listInboxItemsBefore(
                    receiverId,
                    recipientId2.toHexString(),
                    lastDeliveredAtMillis,
                    2
            );

            assertEquals(1, result.size());
            assertEquals(notificationId1.toHexString(), result.get(0).notificationId());
            assertEquals(recipientId1.toHexString(), result.get(0).recipientId());

            verify(notificationRecipientRepository).listHistoricalBefore(receiverId, recipientId2, lastDeliveredAt, 2);
            verify(notificationRepository).findByIdInAndDeletedFalseFromSecondary(Set.of(notificationId1, notificationId2));
            verify(notificationRepository, never()).findByIdInAndDeletedFalse(anySet());
        }
    }

    @Nested
    @DisplayName("markAsRead")
    class MarkAsReadTest {

        @Test
        @DisplayName("알림을 읽음 처리하면 Recipient를 read=true로 수정한다")
        void markAsRead() {
            Instant now = Instant.parse("2026-06-23T01:00:00Z");

            when(clock.now()).thenReturn(now);
            when(notificationRecipientRepository.markAsRead(notificationId1, receiverId, now))
                    .thenReturn(1L);

            boolean result = sut.markAsRead(notificationId1.toHexString(), receiverId);

            assertTrue(result);

            verify(clock).now();
            verify(notificationRecipientRepository).markAsRead(notificationId1, receiverId, now);
            verifyNoInteractions(notificationRepository);
        }

        @Test
        @DisplayName("읽음 처리 대상이 없으면 false를 반환한다")
        void markAsReadWhenNotModified() {
            Instant now = Instant.parse("2026-06-23T01:00:00Z");

            when(clock.now()).thenReturn(now);
            when(notificationRecipientRepository.markAsRead(notificationId1, receiverId, now))
                    .thenReturn(0L);

            boolean result = sut.markAsRead(notificationId1.toHexString(), receiverId);

            assertFalse(result);

            verify(clock).now();
            verify(notificationRecipientRepository).markAsRead(notificationId1, receiverId, now);
            verifyNoInteractions(notificationRepository);
        }

        @Test
        @DisplayName("notificationId가 유효하지 않으면 false를 반환한다")
        void markAsReadWhenNotificationIdIsInvalid() {
            boolean result = sut.markAsRead("invalid-object-id", receiverId);

            assertFalse(result);

            verifyNoInteractions(clock);
            verifyNoInteractions(notificationRecipientRepository);
            verifyNoInteractions(notificationRepository);
        }

        @Test
        @DisplayName("receiverId가 null이면 false를 반환한다")
        void markAsReadWhenReceiverIdIsNull() {
            boolean result = sut.markAsRead(notificationId1.toHexString(), null);

            assertFalse(result);

            verifyNoInteractions(clock);
            verifyNoInteractions(notificationRecipientRepository);
            verifyNoInteractions(notificationRepository);
        }
    }

    private MongoNotification mongoNotification(
            ObjectId notificationId,
            String title,
            String message,
            LocalDateTime createdAt
    ) {
        Notification notification = notification(
                notificationId.toHexString(),
                title,
                message,
                createdAt
        );

        return MongoNotification.fromDomain(notification);
    }

    private Notification notification(
            String notificationId,
            String title,
            String message,
            LocalDateTime createdAt
    ) {
        return Notification.rehydrate(
                notificationId,
                NotificationType.PRICE_ALERT,
                title,
                message,
                List.of(),
                null,
                Map.of(),
                false,
                null,
                createdAt
        );
    }

    private MongoNotificationRecipient mongoRecipient(
            ObjectId recipientId,
            ObjectId notificationId,
            boolean read,
            LocalDateTime deliveredAt
    ) {
        NotificationRecipient recipient = NotificationRecipient.rehydrate(
                recipientId.toHexString(),
                notificationId.toHexString(),
                receiverId,
                read,
                read ? deliveredAt.plusMinutes(1) : null,
                deliveredAt
        );

        return MongoNotificationRecipient.fromDomain(recipient);
    }

    private long getCreatedAtMs(LocalDateTime dateTime) {
        return dateTime
                .atZone(ServiceZoneUtils.ZONE_ID)
                .toInstant()
                .toEpochMilli();
    }
}