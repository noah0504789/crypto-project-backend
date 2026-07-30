package org.example.common.inbox.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.inbox.domain.event.AbstractInboxEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractInboxEventTest {

    @Test
    @DisplayName("기본 생성자는 매번 새로운 UUID eventId를 만든다")
    void createsRandomUUID() {
        TestInboxEvent first = new TestInboxEvent();
        TestInboxEvent second = new TestInboxEvent();

        assertThat(first.getEventId()).hasSize(36);
        assertThat(second.getEventId()).hasSize(36);
        assertThat(second.getEventId()).isNotEqualTo(first.getEventId());
    }

    @Test
    @DisplayName("eventId는 Kafka payload JSON에서 제외한다")
    void ignoresEventIdDuringSerialization() throws JsonProcessingException {
        String json = new ObjectMapper().writeValueAsString(new TestInboxEvent());

        assertThat(json).doesNotContain("eventId");
    }

    @Test
    @DisplayName("byte 배열 event_id 헤더를 문자열로 추출한다")
    void extractsByteArrayEventId() {
        TestInboxEvent event = new TestInboxEvent();
        Message<TestInboxEvent> message = MessageBuilder.withPayload(event)
                .setHeader("event_id", "event-1".getBytes(StandardCharsets.UTF_8))
                .build();

        assertThat(event.extractEventId(message)).isEqualTo("event-1");
    }

    @Test
    @DisplayName("문자열 event_id 헤더를 추출한다")
    void extractsStringEventId() {
        TestInboxEvent event = new TestInboxEvent();
        Message<TestInboxEvent> message = MessageBuilder.withPayload(event)
                .setHeader("event_id", "event-1")
                .build();

        assertThat(event.extractEventId(message)).isEqualTo("event-1");
    }

    @Test
    @DisplayName("event_id 헤더가 없으면 예외를 던진다")
    void rejectsMissingEventId() {
        TestInboxEvent event = new TestInboxEvent();
        Message<TestInboxEvent> message = MessageBuilder.withPayload(event).build();

        assertThatThrownBy(() -> event.extractEventId(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("event_id header is missing");
    }

    private static final class TestInboxEvent extends AbstractInboxEvent {

        public String getCode() {
            return "KRW-BTC";
        }
    }
}
