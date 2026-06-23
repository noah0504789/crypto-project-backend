package org.example.websocket.gateway.adapter.in.event.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.StompTopic;
import org.example.notification.contract.event.WebNotificationEvent;
import org.example.notification.contract.event.WebNotificationPayload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebNotificationEventHandler {

    private final SimpMessagingTemplate stompTemplate;

    public void handle(WebNotificationEvent event, String txId) {
        boolean sent = sendWebNotification(event, txId);

        if (sent) {
            log.debug("✅ [STOMP] web notification 성공: txId={}", txId);
            return;
        }

        log.debug("[STOMP] web notification skip/fail: txId={}", txId);
    }

    private boolean sendWebNotification(WebNotificationEvent event, String txId) {
        if (event == null) {
            log.warn("[STOMP] web notification ignored. txId={}, event-null=true", txId);
            return false;
        }

        WebNotificationPayload payload = event.getPayload();
        String partitionKey = event.getPartitionKey();

        if (payload == null || partitionKey == null || partitionKey.isBlank()) {
            log.warn(
                    "[STOMP] web notification ignored. txId={}, partitionKey={}, payload-null={}",
                    txId,
                    partitionKey,
                    payload == null
            );

            return false;
        }

        String destination = StompTopic.NOTIFICATION.destination(partitionKey);

        try {
            stompTemplate.convertAndSend(destination, payload);

            log.debug(
                    "[STOMP] web notification sent. txId={}, notificationId={}, partitionKey={}, destination={}",
                    txId,
                    event.getNotificationId(),
                    partitionKey,
                    destination
            );

            return true;
        } catch (Exception e) {
            log.error(
                    "❌ [STOMP] web notification 실패: txId={}, destination={}, error={}",
                    txId,
                    destination,
                    e.getMessage(),
                    e
            );

            return false;
        }
    }
}