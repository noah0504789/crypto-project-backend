package org.example.upbit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.test.config.TestBootApplication;
import config.TestPropertiesConfig;
import config.TestUpbitExternalDependencyConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.apache.kafka.streams.state.WindowStore;
import org.example.infra.properties.UpbitProperties;
import org.example.upbit.event.UpbitTickerEvent;
import org.example.upbit.event.UpbitTickerValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.messaging.Message;

import java.time.Instant;
import java.util.Properties;
import org.example.common.event.notification.WebNotificationEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {
        TestBootApplication.class,

        // 실제 테스트 대상
        UpbitWebsocketService.class,

        // 테스트 설정
        TestPropertiesConfig.class,
        TestUpbitExternalDependencyConfig.class
})
class UpbitTickerProcessorTopologyTest {

    private static final String INPUT_TOPIC = "upbit-ticker-in";
    private static final String CODE = "KRW-BTC";

    @Autowired
    private UpbitProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StreamBridge streamBridge;

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, UpbitTickerEvent> inputTopic;

    @BeforeEach
    void setUp() {
        streamBridge = mockStreamBridge();

        StreamsBuilder builder = new StreamsBuilder();
        String storeName = properties.store().ticker().name();

        addTickerWindowStore(builder, storeName);
        addTickerProcessor(builder, storeName);

        testDriver = createTopologyTestDriver(builder);
        inputTopic = createInputTopic(testDriver);
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
    @DisplayName("평균 대비 변화율이 기준 이하이면 알림을 발행하지 않는다")
    void process_belowThreshold_doNotPublishNotification() {
        // given & when
        inputTopic.pipeInput(CODE, tickerEvent(CODE, 100.0), Instant.ofEpochMilli(1_000L));
        inputTopic.pipeInput(CODE, tickerEvent(CODE, 100.0), Instant.ofEpochMilli(2_000L));
        inputTopic.pipeInput(CODE, tickerEvent(CODE, 102.0), Instant.ofEpochMilli(3_000L));

        // then
        verify(streamBridge, never()).send(anyString(), any(Message.class));
    }

    @Test
    @DisplayName("평균 대비 변화율이 기준 초과이면 WebNotificationEvent를 발행한다")
    void process_overThreshold_publishNotification() {
        // given & when
        inputTopic.pipeInput(CODE, tickerEvent(CODE, 100.0), Instant.ofEpochMilli(1_000L));
        inputTopic.pipeInput(CODE, tickerEvent(CODE, 100.0), Instant.ofEpochMilli(2_000L));
        inputTopic.pipeInput(CODE, tickerEvent(CODE, 110.0), Instant.ofEpochMilli(3_000L));

        // then
        ArgumentCaptor<Message<?>> messageCaptor = ArgumentCaptor.forClass(Message.class);

        verify(streamBridge, times(1)).send(
                anyString(),
                messageCaptor.capture()
        );

        Message<?> message = messageCaptor.getValue();

        assertThat(message.getPayload()).isInstanceOf(WebNotificationEvent.class);

        WebNotificationEvent notification = (WebNotificationEvent) message.getPayload();

        assertThat(notification.eventType()).isEqualTo("UpbitTickerAlertEvent");
        assertThat(notification.getPartitionKey()).isEqualTo(CODE);

        assertThat(notification.payload().data())
                .containsEntry("code", CODE)
                .containsEntry("price", 110.0)
                .containsEntry("avgInterval", properties.ticker().alert().windowMinutes())
                .containsEntry("avgPrice", 100.0)
                .containsEntry("changeRate", 0.1);
    }

    @Test
    @DisplayName("3분 윈도우 밖의 데이터는 평균 계산에서 제외한다")
    void process_excludeOldDataOutsideWindow() {
        // given
        inputTopic.pipeInput(CODE, tickerEvent(CODE, 50.0), Instant.ofEpochMilli(1_000L));

        // 4분 뒤라서 1초 데이터는 최근 3분 윈도우 밖
        inputTopic.pipeInput(CODE, tickerEvent(CODE, 100.0), Instant.ofEpochMilli(241_000L));
        inputTopic.pipeInput(CODE, tickerEvent(CODE, 110.0), Instant.ofEpochMilli(242_000L));

        // then
        verify(streamBridge, times(1)).send(anyString(), any(Message.class));
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

    private StreamBridge mockStreamBridge() {
        StreamBridge mockedStreamBridge = mock(StreamBridge.class);

        when(mockedStreamBridge.send(anyString(), any(Message.class)))
                .thenReturn(true);

        return mockedStreamBridge;
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

        stream.process(
                () -> new UpbitTickerProcessor(streamBridge, objectMapper, properties),
                Named.as("upbit-ticker-watcher"),
                storeName
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
}
