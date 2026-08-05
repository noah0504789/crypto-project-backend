package org.example.marketdetection.upbit;

import org.example.common.test.config.TestBootApplication;
import config.TestPropertiesConfig;
import config.TestUpbitExternalDependencyConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowStore;
import org.example.marketdetection.contract.event.PriceAlertDetectedEvent;
import org.example.common.time.Clock;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.example.marketdetection.upbit.event.UpbitTickerEvent;
import org.example.marketdetection.upbit.event.UpbitTickerValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Instant;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
@SpringBootTest(classes = {
        TestBootApplication.class,

        // 실제 테스트 대상
        UpbitWebsocketService.class,

        // 테스트 설정
        TestPropertiesConfig.class,
        TestUpbitExternalDependencyConfig.class
})
class UpbitTickerProcessorTopologyIntegrationTest {

    private static final String INPUT_TOPIC = "upbit-ticker-in";
    private static final String OUTPUT_TOPIC = "price-alert-detected-out";
    private static final String CODE = "KRW-BTC";

    @Autowired
    private UpbitProperties properties;

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, UpbitTickerEvent> inputTopic;
    private TestOutputTopic<String, PriceAlertDetectedEvent> outputTopic;
    private final Clock clock = mock(Clock.class);

    @BeforeEach
    void setUp() {
        when(clock.nowMs()).thenReturn(0L);
        StreamsBuilder builder = new StreamsBuilder();
        String storeName = properties.store().ticker().name();

        addTickerWindowStore(builder, storeName);
        addTickerProcessor(builder, storeName);

        testDriver = createTopologyTestDriver(builder);
        inputTopic = createInputTopic(testDriver);
        outputTopic = createOutputTopic(testDriver);
    }

    @AfterEach
    void tearDown() {
        if (testDriver != null) {
            testDriver.close();
        }
    }

    @Test
    @DisplayName("ticker 이벤트가 들어오면 WindowStore에 현재 가격을 저장한다")
    void process_saveTickerValueToWindowStore() {
        // given
        long timestamp = 1_000L;

        // when
        inputTopic.pipeInput(
                CODE,
                tickerEvent(CODE, 100.0),
                Instant.ofEpochMilli(timestamp)
        );

        // then
        WindowStore<String, UpbitTickerValue> store =
                testDriver.getWindowStore(properties.store().ticker().name());

        try (var iterator = store.fetch(CODE, 0L, timestamp)) {
            assertThat(iterator.hasNext()).isTrue();

            var saved = iterator.next();

            assertThat(saved.value.price()).isEqualTo(100.0);
            assertThat(saved.value.timestamp()).isEqualTo(timestamp);
        }
    }

    @Test
    @DisplayName("평균 대비 변화율이 3% 미만이면 0% 알림 감지 이벤트만 발행한다")
    void process_belowThreshold_publishZeroPercentEvents() {
        // given & when
        inputTopic.pipeInput(CODE, tickerEvent(CODE, 100.0), Instant.ofEpochMilli(1_000L));
        inputTopic.pipeInput(CODE, tickerEvent(CODE, 100.0), Instant.ofEpochMilli(2_000L));
        inputTopic.pipeInput(CODE, tickerEvent(CODE, 102.0), Instant.ofEpochMilli(3_000L));

        // then
        assertThat(outputTopic.readValuesToList())
                .extracting(PriceAlertDetectedEvent::getThreshold)
                .containsOnly("PERCENT_0");
    }

    @Test
    @DisplayName("평균 대비 변화율이 여러 기준을 초과하면 기준별 PriceAlertDetectedEvent를 각각 발행한다")
    void process_overThreshold_publishPriceAlertDetectedEventsByThreshold() {
        // given & when
        inputTopic.pipeInput(CODE, tickerEvent(CODE, 100.0), Instant.ofEpochMilli(1_000L));
        inputTopic.pipeInput(CODE, tickerEvent(CODE, 100.0), Instant.ofEpochMilli(2_000L));
        outputTopic.readValuesToList();
        inputTopic.pipeInput(CODE, tickerEvent(CODE, 110.0), Instant.ofEpochMilli(3_000L));

        // then
        List<PriceAlertDetectedEvent> events = outputTopic.readValuesToList();

        assertThat(events)
                .extracting(PriceAlertDetectedEvent::getPartitionKey)
                .containsOnly(CODE);

        assertThat(events)
                .extracting(PriceAlertDetectedEvent::getThreshold)
                .containsExactlyInAnyOrder(
                        "PERCENT_0",
                        "PERCENT_3",
                        "PERCENT_5",
                        "PERCENT_7"
                );

        for (PriceAlertDetectedEvent event : events) {
            assertThat(event.getEventId()).isNotBlank();
            assertThat(event.getCode()).isEqualTo(CODE);
            assertThat(event.getPrice()).isEqualTo(110.0);
            assertThat(event.getTimestamp()).isEqualTo(3_000L);
            assertThat(event.getAvgInterval()).isEqualTo(properties.ticker().alert().windowMinutes());
            assertThat(event.getAvgPrice()).isEqualTo(100.0);
            assertThat(event.getChangeRate()).isEqualTo(0.1);
        }
    }

    @Test
    @DisplayName("3분 윈도우 밖의 데이터는 평균 계산에서 제외한다")
    void process_excludeOldDataOutsideWindow() {
        // given
        inputTopic.pipeInput(CODE, tickerEvent(CODE, 50.0), Instant.ofEpochMilli(1_000L));

        // 4분 뒤라서 1초 데이터는 최근 3분 윈도우 밖
        inputTopic.pipeInput(CODE, tickerEvent(CODE, 100.0), Instant.ofEpochMilli(241_000L));
        outputTopic.readValuesToList();
        inputTopic.pipeInput(CODE, tickerEvent(CODE, 110.0), Instant.ofEpochMilli(242_000L));

        // then
        List<PriceAlertDetectedEvent> events = outputTopic.readValuesToList();

        assertThat(events)
                .extracting(PriceAlertDetectedEvent::getPartitionKey)
                .containsOnly(CODE);

        assertThat(events)
                .extracting(PriceAlertDetectedEvent::getThreshold)
                .containsExactlyInAnyOrder(
                        "PERCENT_0",
                        "PERCENT_3",
                        "PERCENT_5",
                        "PERCENT_7"
                );

        for (PriceAlertDetectedEvent event : events) {
            assertThat(event.getEventId()).isNotBlank();
            assertThat(event.getCode()).isEqualTo(CODE);
            assertThat(event.getPrice()).isEqualTo(110.0);
            assertThat(event.getTimestamp()).isEqualTo(242_000L);
            assertThat(event.getAvgInterval()).isEqualTo(properties.ticker().alert().windowMinutes());
            assertThat(event.getAvgPrice()).isEqualTo(100.0);
            assertThat(event.getChangeRate()).isEqualTo(0.1);
        }
    }

    private UpbitTickerEvent tickerEvent(String code, Double tradePrice) {
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
                null,
                null,
                null
        );
    }

    private void addTickerWindowStore(StreamsBuilder builder, String storeName) {
        StoreBuilder<WindowStore<String, UpbitTickerValue>> storeBuilder =
                Stores.windowStoreBuilder(
                        Stores.persistentWindowStore(
                                storeName,
                                properties.store().ticker().retention(),
                                properties.store().ticker().windowSize(),
                                properties.store().ticker().retainDuplicates()
                        ),
                        Serdes.String(),
                        new JsonSerde<>(UpbitTickerValue.class)
                );

        builder.addStateStore(storeBuilder);
    }

    private void addTickerProcessor(StreamsBuilder builder, String storeName) {
        KStream<String, UpbitTickerEvent> stream = builder.stream(
                INPUT_TOPIC,
                Consumed.with(
                        Serdes.String(),
                        new JsonSerde<>(UpbitTickerEvent.class)
                )
        );

        stream.<String, PriceAlertDetectedEvent>process(
                () -> new UpbitTickerProcessor(properties, clock),
                Named.as("upbit-ticker-watcher"),
                storeName
        ).to(
                OUTPUT_TOPIC,
                Produced.with(Serdes.String(), new JsonSerde<>(PriceAlertDetectedEvent.class))
        );
    }

    private TopologyTestDriver createTopologyTestDriver(StreamsBuilder builder) {
        Properties streamProperties = new Properties();

        streamProperties.put(
                StreamsConfig.APPLICATION_ID_CONFIG,
                "upbit-ticker-processor-test"
        );

        streamProperties.put(
                StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,
                "dummy:9092"
        );

        streamProperties.put(
                StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG,
                Serdes.StringSerde.class.getName()
        );

        return new TopologyTestDriver(builder.build(), streamProperties);
    }

    private TestInputTopic<String, UpbitTickerEvent> createInputTopic(TopologyTestDriver testDriver) {
        return testDriver.createInputTopic(
                INPUT_TOPIC,
                Serdes.String().serializer(),
                new JsonSerde<>(UpbitTickerEvent.class).serializer()
        );
    }

    private TestOutputTopic<String, PriceAlertDetectedEvent> createOutputTopic(TopologyTestDriver testDriver) {
        return testDriver.createOutputTopic(
                OUTPUT_TOPIC,
                Serdes.String().deserializer(),
                new JsonSerde<>(PriceAlertDetectedEvent.class).deserializer()
        );
    }
}
