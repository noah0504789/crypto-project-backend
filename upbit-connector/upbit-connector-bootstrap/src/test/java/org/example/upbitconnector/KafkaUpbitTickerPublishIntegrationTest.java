package org.example.upbitconnector;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.example.common.test.testcontainer.KafkaTestContainerInitializer;
import org.example.upbitconnector.application.port.out.UpbitTickerPublishPort;
import org.example.upbitconnector.contract.event.UpbitTickerEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ContextConfiguration;

/**
 * 발행한 시세를 소비자 관점에서 되읽어 wire 계약을 고정한다.
 *
 * <p>이 바인딩은 {@code value.serializer}로 JsonSerializer를 쓴다(market-detection과 동일). 이 구성에서는 {@code
 * KafkaEventFactory}가 넣는 {@code __TypeId__}가 브로커까지 전달되지 않으므로, 소비자는 헤더가 아니라 <b>선언된 타입</b>으로 역직렬화해야
 * 한다. 이 단정이 깨지면 수집 이관의 전제도 함께 재검토해야 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(initializers = KafkaTestContainerInitializer.class)
class KafkaUpbitTickerPublishIntegrationTest {

    private static final String TOPIC = "upbit-ticker-event";
    private static final String TYPE_ID_HEADER = "__TypeId__";

    @Autowired private UpbitTickerPublishPort tickerPublishPort;

    @Autowired private Environment environment;

    @Test
    @DisplayName("발행한 시세는 선언된 타입으로 역직렬화되고 타입 헤더에는 의존하지 않는다")
    void publishedTickerIsConsumableByDeclaredType() {
        // given: Kafka 컨테이너를 재사용하므로 이번 실행의 레코드만 골라내도록 고유 키를 쓴다
        String code = "KRW-TEST-" + UUID.randomUUID();
        UpbitTickerEvent event = ticker(code, 42_000_000.0);

        try (KafkaConsumer<String, UpbitTickerEvent> consumer = consumer()) {
            consumer.subscribe(List.of(TOPIC));

            // when
            tickerPublishPort.publish(event).block(Duration.ofSeconds(10));

            // then
            ConsumerRecord<String, UpbitTickerEvent> record = poll(consumer, code);

            assertThat(record.key()).isEqualTo(code);
            assertThat(record.value().code()).isEqualTo(code);
            assertThat(record.value().tradePrice()).isEqualTo(42_000_000.0);
            assertThat(record.value().tradeTimestamp()).isEqualTo(1L);
            assertThat(typeIdHeader(record)).isNull();
        }
    }

    private ConsumerRecord<String, UpbitTickerEvent> poll(
            KafkaConsumer<String, UpbitTickerEvent> consumer, String code) {
        for (int attempt = 0; attempt < 30; attempt++) {
            ConsumerRecords<String, UpbitTickerEvent> records =
                    consumer.poll(Duration.ofSeconds(1));

            for (ConsumerRecord<String, UpbitTickerEvent> record : records) {
                if (code.equals(record.key())) {
                    return record;
                }
            }
        }

        throw new AssertionError("발행한 ticker 레코드를 소비하지 못했다. topic=" + TOPIC + " key=" + code);
    }

    private String typeIdHeader(ConsumerRecord<String, UpbitTickerEvent> record) {
        Header header = record.headers().lastHeader(TYPE_ID_HEADER);

        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private KafkaConsumer<String, UpbitTickerEvent> consumer() {
        // 소비자가 타입 헤더 없이 선언된 타입으로 읽는 경로를 그대로 재현한다.
        JsonDeserializer<UpbitTickerEvent> valueDeserializer =
                new JsonDeserializer<>(UpbitTickerEvent.class);
        valueDeserializer.setUseTypeHeaders(false);

        Map<String, Object> configs =
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                        environment.getRequiredProperty("spring.kafka.bootstrap-servers"),
                        ConsumerConfig.GROUP_ID_CONFIG,
                        "upbit-connector-test-" + UUID.randomUUID(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                        "earliest",
                        ConsumerConfig.ISOLATION_LEVEL_CONFIG,
                        "read_committed");

        return new KafkaConsumer<>(configs, new StringDeserializer(), valueDeserializer);
    }

    private UpbitTickerEvent ticker(String code, Double tradePrice) {
        return new UpbitTickerEvent(
                "ticker",
                code,
                null,
                null,
                null,
                tradePrice,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1L,
                null);
    }
}
