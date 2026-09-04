package org.example.marketdetection.adapter.in.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowStore;
import org.example.common.enums.KafkaHeaderKey;
import org.example.common.time.Clock;
import org.example.marketdetection.application.properties.PriceAlertDetectionProperties;
import org.example.marketdetection.application.service.PriceAlertDetectionService;
import org.example.marketdetection.contract.event.PriceAlertDetectedEvent;
import org.example.marketdetection.application.dto.PricePoint;
import org.example.upbitconnector.contract.event.UpbitTickerEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.serializer.JsonSerde;

@ExtendWith(MockitoExtension.class)
class PriceAlertDetectionProcessorTopologyIntegrationTest {

    private static final String INPUT_TOPIC = "upbit-ticker-in";
    private static final String OUTPUT_TOPIC = "price-alert-detected-out";
    private static final String STORE_NAME = "upbit-ticker-store";
    private static final String CODE = "KRW-BTC";

    private final PriceAlertDetectionProperties properties =
            new PriceAlertDetectionProperties(
                    3,
                    Duration.ofSeconds(10),
                    new PriceAlertDetectionProperties.Store(
                            STORE_NAME, Duration.ofMinutes(5), Duration.ofMinutes(3), false));

    @Mock private Clock clock;

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, UpbitTickerEvent> inputTopic;
    private TestOutputTopic<String, PriceAlertDetectedEvent> outputTopic;

    @BeforeEach
    void setUp() {
        given(clock.nowMs()).willReturn(0L);

        StreamsBuilder builder = new StreamsBuilder();
        builder.addStateStore(pricePointStore());
        builder.stream(INPUT_TOPIC, Consumed.with(Serdes.String(), new JsonSerde<>(UpbitTickerEvent.class)))
                .process(
                        () ->
                                new PriceAlertDetectionProcessor(
                                        new PriceAlertDetectionService(properties, clock), properties),
                        Named.as("price-alert-detector"),
                        STORE_NAME)
                .to(OUTPUT_TOPIC, Produced.with(Serdes.String(), new JsonSerde<>(PriceAlertDetectedEvent.class)));

        testDriver = new TopologyTestDriver(builder.build(), streamsConfig());
        inputTopic =
                testDriver.createInputTopic(
                        INPUT_TOPIC, Serdes.String().serializer(), new JsonSerde<>(UpbitTickerEvent.class).serializer());
        outputTopic =
                testDriver.createOutputTopic(
                        OUTPUT_TOPIC,
                        Serdes.String().deserializer(),
                        new JsonSerde<>(PriceAlertDetectedEvent.class).deserializer());
    }

    @AfterEach
    void tearDown() {
        if (testDriver != null) {
            testDriver.close();
        }
    }

    @Test
    @DisplayName("ticker 이벤트가 들어오면 표본을 state store 에 저장한다")
    void process_savesTickerSample() {
        // given & when
        inputTopic.pipeInput(CODE, tickerEvent(100.0, 1_000L), Instant.ofEpochMilli(1_000L));

        // then
        WindowStore<String, PricePoint> store = testDriver.getWindowStore(STORE_NAME);

        try (var iterator = store.fetch(CODE, 0L, 1_000L)) {
            assertThat(iterator.hasNext()).isTrue();

            var saved = iterator.next();

            assertThat(saved.value.price()).isEqualTo(100.0);
            assertThat(saved.value.timestamp()).isEqualTo(1_000L);
        }
    }

    @Test
    @DisplayName("stale 판정과 별개로 Kafka record 시각을 상태와 출력에 사용한다")
    void process_usesKafkaRecordTimestampForStore() {
        // given & when
        inputTopic.pipeInput(CODE, tickerEvent(100.0, 1_000L), Instant.ofEpochMilli(5_000L));
        inputTopic.pipeInput(CODE, tickerEvent(110.0, 6_000L), Instant.ofEpochMilli(9_000L));

        // then
        WindowStore<String, PricePoint> store = testDriver.getWindowStore(STORE_NAME);

        try (var iterator = store.fetch(CODE, 5_000L, 5_000L)) {
            assertThat(iterator.hasNext()).isTrue();
            assertThat(iterator.next().value.timestamp()).isEqualTo(5_000L);
        }

        assertThat(outputTopic.readValuesToList())
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.getTimestamp()).isEqualTo(9_000L));
    }

    @Test
    @DisplayName("평균 대비 변동률이 3% 미만이면 이벤트를 발행하지 않는다")
    void process_belowThreshold_publishesNoEvent() {
        // given & when
        inputTopic.pipeInput(CODE, tickerEvent(100.0, 1_000L), Instant.ofEpochMilli(1_000L));
        inputTopic.pipeInput(CODE, tickerEvent(102.0, 2_000L), Instant.ofEpochMilli(2_000L));

        // then
        assertThat(outputTopic.readValuesToList()).isEmpty();
    }

    @Test
    @DisplayName("변동률이 여러 임계를 넘으면 임계마다 이벤트를 발행한다")
    void process_overThreshold_publishesEventPerThreshold() {
        // given
        inputTopic.pipeInput(CODE, tickerEvent(100.0, 1_000L), Instant.ofEpochMilli(1_000L));
        outputTopic.readValuesToList();

        // when
        inputTopic.pipeInput(CODE, tickerEvent(110.0, 2_000L), Instant.ofEpochMilli(2_000L));

        // then
        List<PriceAlertDetectedEvent> events = outputTopic.readValuesToList();

        assertThat(events)
                .extracting(PriceAlertDetectedEvent::getThreshold)
                .containsExactlyInAnyOrder("PERCENT_3", "PERCENT_5", "PERCENT_7");
        assertThat(events).extracting(PriceAlertDetectedEvent::getPartitionKey).containsOnly(CODE);
    }

    @Test
    @DisplayName("탐지 이벤트의 event id를 Kafka header로 전달한다")
    void process_addsEventIdHeader() {
        // given & when
        inputTopic.pipeInput(CODE, tickerEvent(100.0, 1_000L), Instant.ofEpochMilli(1_000L));
        inputTopic.pipeInput(CODE, tickerEvent(110.0, 2_000L), Instant.ofEpochMilli(2_000L));

        // then
        var outputRecord = outputTopic.readRecord();
        var eventIdHeader = outputRecord.headers().lastHeader(KafkaHeaderKey.EVENT_ID.value());

        assertThat(eventIdHeader).isNotNull();
        String eventId = new String(eventIdHeader.value(), StandardCharsets.UTF_8);

        assertThat(eventId).isNotBlank();
        assertThatCode(() -> UUID.fromString(eventId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("허용 시간이 지난 이벤트는 처리하지 않는다")
    void process_staleEvent_isIgnored() {
        // given
        given(clock.nowMs()).willReturn(100_000L);

        // when
        inputTopic.pipeInput(CODE, tickerEvent(100.0, 1_000L), Instant.ofEpochMilli(100_000L));

        // then
        assertThat(outputTopic.readValuesToList()).isEmpty();
    }

    private StoreBuilder<WindowStore<String, PricePoint>> pricePointStore() {
        return Stores.windowStoreBuilder(
                Stores.persistentWindowStore(
                        STORE_NAME, Duration.ofMinutes(5), Duration.ofMinutes(3), false),
                Serdes.String(),
                new JsonSerde<>(PricePoint.class));
    }

    private Properties streamsConfig() {
        Properties config = new Properties();
        config.put(StreamsConfig.APPLICATION_ID_CONFIG, "price-alert-detection-test");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");

        return config;
    }

    private UpbitTickerEvent tickerEvent(Double tradePrice, Long tradeTimestamp) {
        return new UpbitTickerEvent(
                "ticker", CODE, null, null, null, tradePrice, null, null, null, null, null, null,
                null, null, null, null, null, null, null, tradeTimestamp, null, null, null, null,
                null, null, null, null, null, null, null, null, null, tradeTimestamp, null);
    }
}
