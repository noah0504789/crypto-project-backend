package org.example.notification.application.service;

import org.example.notification.application.port.out.NotificationCachePort;
import org.example.notification.application.port.out.NotificationPersistencePort;
import org.example.notification.application.service.query.ListNotificationInboxItemsQuery;
import org.example.notification.application.service.result.NotificationInboxItem;
import org.example.notification.domain.model.Notification;
import org.example.notification.domain.model.NotificationRecipient;
import org.example.notification.domain.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceUnitTest {

    @Mock
    private NotificationCachePort cache;

    @Mock
    private NotificationPersistencePort persistence;

    @InjectMocks
    private NotificationQueryService sut;

    @Captor
    private ArgumentCaptor<List<Notification>> warmUpCaptor;

    private final UUID receiverId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final String lastRecipientId = "aaaaaaaaaaaaaaaaaaaaaaaa";
    private final Long lastDeliveredAtMs = 1_700_000_000_000L;
    private final int limit = 11;

    @Nested
    @DisplayName("recipient 인덱스 선택")
    class RecipientSource {

        @Test
        @DisplayName("커서가 없으면 최신 recipient 목록을 조회한다")
        void listLatest_whenNoCursor() {
            ListNotificationInboxItemsQuery query = ListNotificationInboxItemsQuery.firstPage(receiverId, limit);
            given(persistence.listLatestRecipients(receiverId, limit)).willReturn(List.of());

            sut.listInboxItems(query);

            verify(persistence).listLatestRecipients(receiverId, limit);
            verify(persistence, never()).listRecipientsBefore(any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("커서가 있으면 커서 이전 recipient 목록을 조회한다")
        void listBefore_whenCursor() {
            ListNotificationInboxItemsQuery query = ListNotificationInboxItemsQuery.prevPage(
                    receiverId, lastRecipientId, lastDeliveredAtMs, limit
            );
            given(persistence.listRecipientsBefore(receiverId, lastRecipientId, lastDeliveredAtMs, limit))
                    .willReturn(List.of());

            sut.listInboxItems(query);

            verify(persistence).listRecipientsBefore(receiverId, lastRecipientId, lastDeliveredAtMs, limit);
            verify(persistence, never()).listLatestRecipients(any(), anyInt());
        }
    }

    @Nested
    @DisplayName("알림 정보 캐시 해석")
    class ResolveNotifications {

        @Test
        @DisplayName("recipient가 없으면 캐시/DB를 호출하지 않고 빈 목록을 반환한다")
        void empty_whenNoRecipients() {
            ListNotificationInboxItemsQuery query = ListNotificationInboxItemsQuery.firstPage(receiverId, limit);
            given(persistence.listLatestRecipients(receiverId, limit)).willReturn(List.of());

            List<NotificationInboxItem> result = sut.listInboxItems(query);

            assertThat(result).isEmpty();
            verify(cache, never()).findByIds(any());
            verify(persistence, never()).findByIds(any());
        }

        @Test
        @DisplayName("캐시 hit이면 DB 재조회/warmUp 없이 캐시로 조립한다")
        void cacheHit_usesCacheWithoutReload() {
            ListNotificationInboxItemsQuery query = ListNotificationInboxItemsQuery.firstPage(receiverId, limit);

            given(persistence.listLatestRecipients(receiverId, limit)).willReturn(List.of(recipient("r1", "n1")));
            given(cache.findByIds(any())).willReturn(Map.of("n1", notification("n1", "제목1")));

            List<NotificationInboxItem> result = sut.listInboxItems(query);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).notificationId()).isEqualTo("n1");
            assertThat(result.get(0).title()).isEqualTo("제목1");
            assertThat(result.get(0).recipientId()).isEqualTo("r1");

            verify(persistence, never()).findByIds(any());
            verify(cache, never()).warmUpAll(any());
        }

        @Test
        @DisplayName("캐시 미스는 DB에서 재조회하고 warm-up 한다")
        void miss_reloadsAndWarmsUp() {
            ListNotificationInboxItemsQuery query = ListNotificationInboxItemsQuery.firstPage(receiverId, limit);

            given(persistence.listLatestRecipients(receiverId, limit)).willReturn(List.of(recipient("r1", "n1")));
            given(cache.findByIds(any())).willReturn(Map.of());
            given(persistence.findByIds(any())).willReturn(List.of(notification("n1", "제목1")));

            List<NotificationInboxItem> result = sut.listInboxItems(query);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).title()).isEqualTo("제목1");

            verify(persistence).findByIds(eq(Set.of("n1")));
            verify(cache).warmUpAll(warmUpCaptor.capture());
            assertThat(warmUpCaptor.getValue()).extracting(Notification::getId).containsExactly("n1");
        }

        @Test
        @DisplayName("일부만 캐시에 있으면 없는 id만 DB에서 재조회한다")
        void partialHit_reloadsOnlyMisses() {
            ListNotificationInboxItemsQuery query = ListNotificationInboxItemsQuery.firstPage(receiverId, limit);

            given(persistence.listLatestRecipients(receiverId, limit))
                    .willReturn(List.of(recipient("r1", "n1"), recipient("r2", "n2")));
            given(cache.findByIds(any())).willReturn(Map.of("n1", notification("n1", "제목1")));
            given(persistence.findByIds(any())).willReturn(List.of(notification("n2", "제목2")));

            List<NotificationInboxItem> result = sut.listInboxItems(query);

            assertThat(result).extracting(NotificationInboxItem::notificationId).containsExactly("n1", "n2");
            verify(persistence).findByIds(eq(Set.of("n2")));
        }

        @Test
        @DisplayName("캐시·DB 모두에 알림이 없으면 해당 recipient는 결과에서 제외한다")
        void skip_whenNotificationMissingEverywhere() {
            ListNotificationInboxItemsQuery query = ListNotificationInboxItemsQuery.firstPage(receiverId, limit);

            given(persistence.listLatestRecipients(receiverId, limit))
                    .willReturn(List.of(recipient("r1", "n1"), recipient("r2", "n2")));
            given(cache.findByIds(any())).willReturn(Map.of("n2", notification("n2", "제목2")));
            given(persistence.findByIds(any())).willReturn(List.of());

            List<NotificationInboxItem> result = sut.listInboxItems(query);

            assertThat(result).extracting(NotificationInboxItem::notificationId).containsExactly("n2");
        }
    }

    private NotificationRecipient recipient(String recipientId, String notificationId) {
        return NotificationRecipient.rehydrate(
                recipientId,
                notificationId,
                receiverId,
                false,
                null,
                LocalDateTime.of(2026, 1, 1, 0, 0)
        );
    }

    private Notification notification(String id, String title) {
        return Notification.rehydrate(
                id,
                NotificationType.PRICE_ALERT,
                title,
                "메시지",
                List.of(),
                null,
                Map.of(),
                false,
                null,
                LocalDateTime.of(2026, 1, 1, 0, 0)
        );
    }
}
