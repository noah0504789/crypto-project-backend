package org.example.notification.adapter.in.web;

import org.example.common.config.MessageConverterConfig;
import org.example.common.test.config.TestBootApplication;
import org.example.notification.application.port.in.NotificationCommandUseCase;
import org.example.notification.application.port.in.NotificationQueryUseCase;
import org.example.notification.application.service.query.ListNotificationInboxItemsQuery;
import org.example.notification.application.service.result.NotificationInboxItem;
import org.example.notification.domain.model.NotificationMessagePart;
import org.example.notification.domain.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.example.common.enums.HttpHeaderKey.USER_ID_VALUE;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@ContextConfiguration(classes = {
        TestBootApplication.class,
        MessageConverterConfig.class,
        NotificationController.class
})
class NotificationControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationQueryUseCase notificationQueryUseCase;

    @MockitoBean
    private NotificationCommandUseCase notificationCommandUseCase;

    private final UUID receiverId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Nested
    @DisplayName("GET /notifications/me")
    class MyNotificationsTest {

        @Test
        @DisplayName("커서가 없으면 최신 알림 목록 조회 Query로 조회한다")
        void myNotifications_shouldListLatestWhenCursorIsNull() throws Exception {
            // given
            NotificationInboxItem item = createInboxItem(
                    "65f000000000000000000001",
                    "66f000000000000000000001",
                    false,
                    LocalDateTime.of(2026, 1, 1, 10, 0)
            );

            ListNotificationInboxItemsQuery query = ListNotificationInboxItemsQuery.firstPage(receiverId, 11);

            given(notificationQueryUseCase.listInboxItems(query))
                    .willReturn(List.of(item));

            // when & then
            mockMvc.perform(get("/notifications/me")
                            .header(USER_ID_VALUE, receiverId.toString())
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.items[0].id").value("65f000000000000000000001"))
                    .andExpect(jsonPath("$.items[0].recipientId").value("66f000000000000000000001"))
                    .andExpect(jsonPath("$.items[0].title").value("가격 알림"))
                    .andExpect(jsonPath("$.items[0].message").value("KRW-BTC이 7.0% 이상 상승했습니다."))
                    .andExpect(jsonPath("$.items[0].read").value(false))
                    // 커서용 deliveredAtMs(epoch millis) 방출 확인
                    .andExpect(jsonPath("$.items[0].deliveredAtMs").isNumber());

            verify(notificationQueryUseCase).listInboxItems(query);
            verifyNoMoreInteractions(notificationQueryUseCase);
        }

        @Test
        @DisplayName("커서가 있으면 이전 알림 목록 조회 Query로 조회한다")
        void myNotifications_shouldListPrevWhenCursorExists() throws Exception {
            // given
            String lastRecipientId = "66f000000000000000000099";
            Long lastDeliveredAtMs = 1767229200000L;

            NotificationInboxItem item = createInboxItem(
                    "65f000000000000000000001",
                    "66f000000000000000000001",
                    true,
                    LocalDateTime.of(2026, 1, 1, 9, 0)
            );

            ListNotificationInboxItemsQuery query = ListNotificationInboxItemsQuery.prevPage(
                    receiverId,
                    lastRecipientId,
                    lastDeliveredAtMs,
                    11
            );

            given(notificationQueryUseCase.listInboxItems(query))
                    .willReturn(List.of(item));

            // when & then
            mockMvc.perform(get("/notifications/me")
                            .header(USER_ID_VALUE, receiverId.toString())
                            .param("lastRecipientId", lastRecipientId)
                            .param("lastDeliveredAtMs", String.valueOf(lastDeliveredAtMs))
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.items[0].id").value("65f000000000000000000001"))
                    .andExpect(jsonPath("$.items[0].recipientId").value("66f000000000000000000001"))
                    .andExpect(jsonPath("$.items[0].read").value(true));

            verify(notificationQueryUseCase).listInboxItems(query);
            verifyNoMoreInteractions(notificationQueryUseCase);
        }

        @Test
        @DisplayName("limit보다 1개 더 조회되면 hasNext true를 반환하고 items는 limit만큼 자른다")
        void myNotifications_shouldReturnHasNextTrueWhenItemsSizeGreaterThanLimit() throws Exception {
            // given
            NotificationInboxItem item1 = createInboxItem(
                    "65f000000000000000000001",
                    "66f000000000000000000001",
                    false,
                    LocalDateTime.of(2026, 1, 1, 10, 0)
            );
            NotificationInboxItem item2 = createInboxItem(
                    "65f000000000000000000002",
                    "66f000000000000000000002",
                    false,
                    LocalDateTime.of(2026, 1, 1, 9, 0)
            );
            NotificationInboxItem item3 = createInboxItem(
                    "65f000000000000000000003",
                    "66f000000000000000000003",
                    false,
                    LocalDateTime.of(2026, 1, 1, 8, 0)
            );

            ListNotificationInboxItemsQuery query = ListNotificationInboxItemsQuery.firstPage(receiverId, 3);

            given(notificationQueryUseCase.listInboxItems(query))
                    .willReturn(List.of(item1, item2, item3));

            // when & then
            mockMvc.perform(get("/notifications/me")
                            .header(USER_ID_VALUE, receiverId.toString())
                            .param("limit", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(true))
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.items[0].id").value("65f000000000000000000001"))
                    .andExpect(jsonPath("$.items[1].id").value("65f000000000000000000002"));

            verify(notificationQueryUseCase).listInboxItems(query);
            verifyNoMoreInteractions(notificationQueryUseCase);
        }

        @Test
        @DisplayName("조회 결과가 없으면 items 빈 배열, hasNext false를 반환한다")
        void myNotifications_shouldReturnEmptyCursorPageWhenItemsIsEmpty() throws Exception {
            // given
            ListNotificationInboxItemsQuery query = ListNotificationInboxItemsQuery.firstPage(receiverId, 11);

            given(notificationQueryUseCase.listInboxItems(query))
                    .willReturn(List.of());

            // when & then
            mockMvc.perform(get("/notifications/me")
                            .header(USER_ID_VALUE, receiverId.toString())
                            .param("limit", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items").isEmpty())
                    .andExpect(jsonPath("$.hasNext").value(false));

            verify(notificationQueryUseCase).listInboxItems(query);
            verifyNoMoreInteractions(notificationQueryUseCase);
        }

        @Test
        @DisplayName("limit이 없으면 기본 limit 10을 사용하고 limitPlus1인 11개를 조회한다")
        void myNotifications_shouldUseDefaultLimitWhenLimitIsMissing() throws Exception {
            // given
            ListNotificationInboxItemsQuery query = ListNotificationInboxItemsQuery.firstPage(receiverId, 11);

            given(notificationQueryUseCase.listInboxItems(query))
                    .willReturn(List.of());

            // when & then
            mockMvc.perform(get("/notifications/me")
                            .header(USER_ID_VALUE, receiverId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(false));

            verify(notificationQueryUseCase).listInboxItems(query);
            verifyNoMoreInteractions(notificationQueryUseCase);
        }
    }

    @Nested
    @DisplayName("PATCH /notifications/{notificationId}/read")
    class ReadNotificationTest {

        @Test
        @DisplayName("읽음 처리에 성공하면 204를 반환한다")
        void readNotification_shouldReturnNoContentWhenMarked() throws Exception {
            String notificationId = "65f000000000000000000001";

            given(notificationCommandUseCase.markAsRead(notificationId, receiverId))
                    .willReturn(true);

            mockMvc.perform(patch("/notifications/{notificationId}/read", notificationId)
                            .header(USER_ID_VALUE, receiverId.toString()))
                    .andExpect(status().isNoContent());

            verify(notificationCommandUseCase).markAsRead(notificationId, receiverId);
        }

        @Test
        @DisplayName("읽음 처리 대상이 없으면 404를 반환한다")
        void readNotification_shouldReturnNotFoundWhenNotMarked() throws Exception {
            String notificationId = "65f000000000000000000001";

            given(notificationCommandUseCase.markAsRead(notificationId, receiverId))
                    .willReturn(false);

            mockMvc.perform(patch("/notifications/{notificationId}/read", notificationId)
                            .header(USER_ID_VALUE, receiverId.toString()))
                    .andExpect(status().isNotFound());

            verify(notificationCommandUseCase).markAsRead(notificationId, receiverId);
        }
    }

    private NotificationInboxItem createInboxItem(
            String notificationId,
            String recipientId,
            boolean read,
            LocalDateTime deliveredAt
    ) {
        return new NotificationInboxItem(
                notificationId,
                recipientId,
                NotificationType.PRICE_ALERT,
                "가격 알림",
                "KRW-BTC이 7.0% 이상 상승했습니다.",
                List.of(
                        NotificationMessagePart.bold("KRW-BTC"),
                        NotificationMessagePart.plain("이 7.0% 이상 상승했습니다.")
                ),
                null,
                Map.of(
                        "marketCode", "KRW-BTC",
                        "threshold", "PERCENT_7"
                ),
                read,
                read ? deliveredAt.plusMinutes(1) : null,
                deliveredAt,
                deliveredAt
        );
    }
}