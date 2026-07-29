package org.example.notification.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.example.common.dto.CursorPage;
import org.example.common.dto.CursorPages;
import org.example.notification.adapter.in.web.dto.NotificationCursor;
import org.example.notification.adapter.in.web.dto.NotificationResponse;
import org.example.notification.application.port.in.NotificationCommandUseCase;
import org.example.notification.application.port.in.NotificationQueryUseCase;
import org.example.notification.application.service.query.ListNotificationInboxItemsQuery;
import org.example.notification.application.service.result.NotificationInboxItem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.example.common.enums.HttpHeaderKey.USER_ID_VALUE;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryUseCase notificationQueryUseCase;
    private final NotificationCommandUseCase notificationCommandUseCase;

    @GetMapping("${api-path.notification.me:/notifications/me}")
    public ResponseEntity<CursorPage<NotificationResponse>> myNotifications(
            @RequestHeader(USER_ID_VALUE) UUID receiverId,
            @ModelAttribute NotificationCursor cursor,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit
    ) {
        int limitPlus1 = limit + 1;

        ListNotificationInboxItemsQuery query = cursor.isNull()
                ? ListNotificationInboxItemsQuery.firstPage(receiverId, limitPlus1)
                : ListNotificationInboxItemsQuery.prevPage(receiverId, cursor.lastRecipientId(), cursor.lastDeliveredAtMs(), limitPlus1);

        List<NotificationInboxItem> items = notificationQueryUseCase.listInboxItems(query);

        return ResponseEntity.ok(
                CursorPages.from(items, limit, NotificationResponse::from)
        );
    }

    @PatchMapping("${api-path.notification.read:/notifications/{notificationId}/read}")
    public ResponseEntity<Void> readNotification(
            @RequestHeader(USER_ID_VALUE) UUID receiverId,
            @PathVariable String notificationId
    ) {
        boolean marked = notificationCommandUseCase.markAsRead(notificationId, receiverId);

        if (!marked) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }
}
