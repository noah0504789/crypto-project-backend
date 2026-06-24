package org.example.notification.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.example.common.dto.CursorPage;
import org.example.notification.adapter.in.web.dto.NotificationCursor;
import org.example.notification.adapter.in.web.dto.NotificationResponse;
import org.example.notification.application.port.in.NotificationCommandUseCase;
import org.example.notification.application.port.in.NotificationQueryUseCase;
import org.example.notification.application.service.query.NotificationInboxItem;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.example.common.enums.HttpHeaderKey.USER_ID_VALUE;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationQueryUseCase notificationQueryUseCase;
    private final NotificationCommandUseCase notificationCommandUseCase;

    @GetMapping("/notifications/me")
    public ResponseEntity<CursorPage<NotificationResponse>> myNotifications(
            @RequestHeader(USER_ID_VALUE) UUID receiverId,
            @ModelAttribute NotificationCursor cursor,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit
    ) {
        int safeLimit = limit == null || limit <= 0 ? 10 : limit;
        int limitPlus1 = safeLimit + 1;

        List<NotificationInboxItem> items = cursor.isNull()
                ? notificationQueryUseCase.listLatest(receiverId, limitPlus1)
                : notificationQueryUseCase.listPrev(receiverId, cursor.lastRecipientId(), cursor.lastDeliveredAtMillis(), limitPlus1);

        if (items.isEmpty()) {
            return ResponseEntity.ok(new CursorPage<>(null, false));
        }

        boolean hasNext = items.size() > safeLimit;

        if (hasNext) {
            items = items.subList(0, safeLimit);
        }

        return ResponseEntity.ok(
                new CursorPage<>(
                        items.stream()
                                .map(NotificationResponse::from)
                                .toList(),
                        hasNext
                )
        );
    }

    @PatchMapping("/notifications/{notificationId}/read")
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