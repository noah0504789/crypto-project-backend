package org.example.common.inbox.domain.event;

import org.example.common.enums.KafkaHeaderKey;
import org.example.common.util.EventIdUtils;
import org.springframework.messaging.Message;

import java.nio.charset.StandardCharsets;

/**
 * Inbox 멱등 처리 대상 이벤트. 식별자는 payload 가 아니라 Kafka 헤더로만 오간다.
 * 필드로 들고 있으면 역직렬화 때 생성자가 다시 돌아 소비 측 객체에 무의미한 값이 남는다.
 */
public abstract class AbstractInboxEvent {

    /** 발행 시점에 발급한다. 호출할 때마다 새 값이며, 레코드 하나가 곧 이벤트 하나다. */
    public final String issueEventId() {
        return EventIdUtils.generateUUID();
    }

    public final String extractEventId(Message<?> message) {
        Object header = message.getHeaders().get(KafkaHeaderKey.EVENT_ID.value());

        if (header instanceof byte[] bytes) {
            String value = new String(bytes, StandardCharsets.UTF_8);
            if (!value.isBlank()) {
                return value;
            }
        }
        if (header instanceof String value && !value.isBlank()) {
            return value;
        }

        throw new IllegalArgumentException("event_id header is missing");
    }
}
