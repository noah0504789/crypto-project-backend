package org.example.common.outbox.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.outbox.domain.OutboxDomainType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractOutboxEventUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Outbox 컬럼으로 저장되는 파생 값은 payload 에 싣지 않는다")
    void serialize_shouldExcludeDerivedValues() throws Exception {
        String json = objectMapper.writeValueAsString(new TestOutboxEvent("value"));

        assertThat(json).doesNotContain("dispatchType", "domainType", "messageType");
        assertThat(json).doesNotContain("aggregateType", "aggregateId");
    }

    @Test
    @DisplayName("creator 가 선언한 필드만 payload 로 오간다")
    void serialize_shouldKeepDeclaredFields() throws Exception {
        TestOutboxEvent event = new TestOutboxEvent("value");

        String json = objectMapper.writeValueAsString(event);
        TestOutboxEvent restored = objectMapper.readValue(json, TestOutboxEvent.class);

        assertThat(json).contains("\"content\":\"value\"");
        assertThat(restored.getContent()).isEqualTo("value");
    }

    private static final class TestOutboxEvent extends AbstractOutboxEvent {

        private final String content;

        @JsonCreator
        private TestOutboxEvent(@JsonProperty("content") String content) {
            super("test-topic", "aggregate-1", "partition-1");
            this.content = content;
        }

        public String getContent() {
            return content;
        }

        @Override
        public OutboxDomainType getDomainType() {
            return OutboxDomainType.NOTIFICATION;
        }
    }
}
