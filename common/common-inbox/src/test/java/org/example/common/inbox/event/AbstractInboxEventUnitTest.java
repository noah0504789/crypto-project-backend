package org.example.common.inbox.event;

import org.example.common.inbox.domain.event.AbstractInboxEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractInboxEventUnitTest {

    @Test
    @DisplayName("발급할 때마다 새로운 UUID eventId를 만든다")
    void issuesRandomUUID() {
        TestInboxEvent event = new TestInboxEvent();

        String first = event.issueEventId();
        String second = event.issueEventId();

        assertThat(first).hasSize(36);
        assertThat(second).hasSize(36);
        assertThat(second).isNotEqualTo(first);
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
    @DisplayName("발급한 eventId는 객체에 남지 않아 추출 결과와 무관하다")
    void doesNotRetainIssuedEventId() {
        TestInboxEvent event = new TestInboxEvent();
        String issued = event.issueEventId();

        Message<TestInboxEvent> message = MessageBuilder.withPayload(event)
                .setHeader("event_id", "event-1")
                .build();

        assertThat(event.extractEventId(message)).isEqualTo("event-1");
        assertThat(event.extractEventId(message)).isNotEqualTo(issued);
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
