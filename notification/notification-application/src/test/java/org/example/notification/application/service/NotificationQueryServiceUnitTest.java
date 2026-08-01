package org.example.notification.application.service;

import org.example.notification.application.port.out.NotificationPersistencePort;
import org.example.notification.application.service.query.ListNotificationInboxItemsQuery;
import org.example.notification.application.service.result.NotificationInboxItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceUnitTest {

    @Mock
    private NotificationPersistencePort notificationPersistencePort;

    @InjectMocks
    private NotificationQueryService sut;

    private final UUID receiverId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final String lastRecipientId = "recipient-1";
    private final Long lastDeliveredAtMs = 1_700_000_000_000L;
    private final int limit = 11;

    @Nested
    @DisplayName("알림 목록 조회")
    class ListInboxItems {

        @Test
        @DisplayName("커서가 없으면 최신 알림 목록을 조회한다")
        void listInboxItems_shouldListLatestInboxItems_whenQueryHasNoCursor() {
            // given
            ListNotificationInboxItemsQuery query = ListNotificationInboxItemsQuery.firstPage(receiverId, limit);

            List<NotificationInboxItem> expected = List.of(notificationInboxItem(), notificationInboxItem());

            given(notificationPersistencePort.listLatestInboxItems(receiverId, limit))
                    .willReturn(expected);

            // when
            List<NotificationInboxItem> result = sut.listInboxItems(query);

            // then
            assertThat(result).isSameAs(expected);

            verify(notificationPersistencePort).listLatestInboxItems(receiverId, limit);
            verify(notificationPersistencePort, never()).listInboxItemsBefore(
                    receiverId,
                    lastRecipientId,
                    lastDeliveredAtMs,
                    limit
            );
        }

        @Test
        @DisplayName("커서가 있으면 커서 이전 알림 목록을 조회한다")
        void listInboxItems_shouldListInboxItemsBefore_whenQueryHasCursor() {
            // given
            ListNotificationInboxItemsQuery query = ListNotificationInboxItemsQuery.prevPage(
                    receiverId,
                    lastRecipientId,
                    lastDeliveredAtMs,
                    limit
            );

            List<NotificationInboxItem> expected = List.of(
                    notificationInboxItem(),
                    notificationInboxItem()
            );

            given(notificationPersistencePort.listInboxItemsBefore(
                    receiverId,
                    lastRecipientId,
                    lastDeliveredAtMs,
                    limit
            )).willReturn(expected);

            // when
            List<NotificationInboxItem> result = sut.listInboxItems(query);

            // then
            assertThat(result).isSameAs(expected);

            verify(notificationPersistencePort).listInboxItemsBefore(
                    receiverId,
                    lastRecipientId,
                    lastDeliveredAtMs,
                    limit
            );
            verify(notificationPersistencePort, never()).listLatestInboxItems(receiverId, limit);
        }

        @Test
        @DisplayName("lastRecipientId가 비어 있으면 최신 알림 목록을 조회한다")
        void listInboxItems_shouldListLatestInboxItems_whenLastRecipientIdIsBlank() {
            // given
            ListNotificationInboxItemsQuery query = ListNotificationInboxItemsQuery.prevPage(
                    receiverId,
                    " ",
                    lastDeliveredAtMs,
                    limit
            );

            List<NotificationInboxItem> expected = List.of(notificationInboxItem());

            given(notificationPersistencePort.listLatestInboxItems(receiverId, limit))
                    .willReturn(expected);

            // when
            List<NotificationInboxItem> result = sut.listInboxItems(query);

            // then
            assertThat(result).isSameAs(expected);

            verify(notificationPersistencePort).listLatestInboxItems(receiverId, limit);
            verify(notificationPersistencePort, never()).listInboxItemsBefore(
                    receiverId,
                    " ",
                    lastDeliveredAtMs,
                    limit
            );
        }

        @Test
        @DisplayName("lastDeliveredAtMs가 없으면 최신 알림 목록을 조회한다")
        void listInboxItems_shouldListLatestInboxItems_whenLastDeliveredAtMsIsNull() {
            // given
            ListNotificationInboxItemsQuery query = ListNotificationInboxItemsQuery.prevPage(
                    receiverId,
                    lastRecipientId,
                    null,
                    limit
            );

            List<NotificationInboxItem> expected = List.of(notificationInboxItem());

            given(notificationPersistencePort.listLatestInboxItems(receiverId, limit))
                    .willReturn(expected);

            // when
            List<NotificationInboxItem> result = sut.listInboxItems(query);

            // then
            assertThat(result).isSameAs(expected);

            verify(notificationPersistencePort).listLatestInboxItems(receiverId, limit);
            verify(notificationPersistencePort, never()).listInboxItemsBefore(
                    receiverId,
                    lastRecipientId,
                    0L,
                    limit
            );
        }
    }

    private NotificationInboxItem notificationInboxItem() {
        return org.mockito.Mockito.mock(NotificationInboxItem.class);
    }
}