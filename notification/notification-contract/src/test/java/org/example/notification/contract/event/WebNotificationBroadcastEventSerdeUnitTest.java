package org.example.notification.contract.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebNotificationBroadcastEventSerdeUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Outbox payload 로 직렬화한 뒤 같은 값으로 복원된다")
    void roundTrip_shouldRestoreContract() throws Exception {
        PriceAlertData data =
                new PriceAlertData("KRW-BTC", 95_000_000.0, 90_000_000.0, 3, 0.05, "PERCENT_5", 1_757_000_000_000L);

        WebNotificationPayload payload = WebNotificationPayload.withoutLink(
                "PRICE_ALERT",
                "가격 알림",
                "KRW-BTC 가격이 변동했습니다.",
                1_757_000_000_000L,
                List.of(new WebNotificationMessagePart("KRW-BTC", true, false)),
                data
        );

        WebNotificationBroadcastEvent event =
                WebNotificationBroadcastEvent.of(payload, "notification-1", "receiver-1");

        String json = objectMapper.writeValueAsString(event);
        WebNotificationBroadcastEvent restored =
                objectMapper.readValue(json, WebNotificationBroadcastEvent.class);

        assertThat(restored.getNotificationId()).isEqualTo("notification-1");
        assertThat(restored.getPayload().title()).isEqualTo("가격 알림");
        assertThat(restored.getPayload().messageParts()).hasSize(1);
        assertThat(restored.getPayload().data()).isEqualTo(data);
        assertThat(restored.getPayload().data().changeRate()).isEqualTo(0.05);
    }

    @Test
    @DisplayName("게이트웨이가 전달 대상을 정하는 partitionKey 는 payload 로 오간다")
    void roundTrip_shouldKeepPartitionKey() throws Exception {
        WebNotificationPayload payload = WebNotificationPayload.withoutLink(
                "PRICE_ALERT", "가격 알림", "본문", 0L, (PriceAlertData) null);

        WebNotificationBroadcastEvent event =
                WebNotificationBroadcastEvent.of(payload, "notification-1", "receiver-1");

        String json = objectMapper.writeValueAsString(event);

        assertThat(objectMapper.readValue(json, WebNotificationBroadcastEvent.class).getPartitionKey())
                .isEqualTo("receiver-1");
    }
}
