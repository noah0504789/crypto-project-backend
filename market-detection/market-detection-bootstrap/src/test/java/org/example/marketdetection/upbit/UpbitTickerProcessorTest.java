package org.example.marketdetection.upbit;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.example.marketdetection.contract.event.PriceAlertDetectedEvent;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.example.marketdetection.upbit.event.UpbitTickerEvent;
import org.example.marketdetection.upbit.event.UpbitTickerValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpbitTickerProcessorTest {

    private static final String STORE_NAME = "upbit-ticker-store";
    private static final String CODE = "KRW-BTC";
    private static final long TIMESTAMP = 1_000_000L;

    private final UpbitProperties properties = createProperties();

    @Mock
    private ProcessorContext<String, PriceAlertDetectedEvent> context;

    @Mock
    private WindowStore<String, UpbitTickerValue> upbitTickerStore;

    private UpbitTickerProcessor sut;

    @BeforeEach
    void setUp() {
        given(context.getStateStore(STORE_NAME)).willReturn(upbitTickerStore);

        sut = new UpbitTickerProcessor(properties);

        sut.init(context);
    }

    @Test
    @DisplayName("현재가가 null이면 저장과 알림 감지 이벤트 발행을 하지 않는다")
    void process_tradePriceIsNull_doNothing() {
        // given
        UpbitTickerEvent event = tickerEvent(null);
        Record<String, UpbitTickerEvent> record = new Record<>(CODE, event, TIMESTAMP);

        // when
        sut.process(record);

        // then
        verifyNoInteractions(upbitTickerStore);
        verify(context, never()).forward(any());
    }

    @Test
    @DisplayName("평균 대비 변화율이 기준 이하이면 현재 값만 저장하고 알림 감지 이벤트는 발행하지 않는다")
    void process_changeRateBelowThreshold_saveOnly() {
        // given
        UpbitTickerEvent event = tickerEvent(102.0);
        Record<String, UpbitTickerEvent> record = new Record<>(CODE, event, TIMESTAMP);

        WindowStoreIterator<UpbitTickerValue> iterator = mockTickerIterator(
                new UpbitTickerValue(100.0, TIMESTAMP - 2_000),
                new UpbitTickerValue(100.0, TIMESTAMP - 1_000)
        );

        given(upbitTickerStore.fetch(
                eq(CODE),
                eq(TIMESTAMP - Duration.ofMinutes(3).toMillis()),
                eq(TIMESTAMP)
        )).willReturn(iterator);

        // when
        sut.process(record);

        // then
        verify(upbitTickerStore).put(
                eq(CODE),
                eq(new UpbitTickerValue(102.0, TIMESTAMP)),
                eq(TIMESTAMP)
        );

        verify(context, never()).forward(any());
    }

    @Test
    @DisplayName("평균 대비 변화율이 여러 기준을 초과하면 기준별 가격 알림 감지 이벤트를 각각 발행한다")
    void process_changeRateOverThreshold_publishPriceAlertDetectedEventsByThreshold() {
        // given
        UpbitTickerEvent event = tickerEvent(110.0);
        Record<String, UpbitTickerEvent> record = new Record<>(CODE, event, TIMESTAMP);

        WindowStoreIterator<UpbitTickerValue> iterator = mockTickerIterator(
                new UpbitTickerValue(100.0, TIMESTAMP - 2_000),
                new UpbitTickerValue(100.0, TIMESTAMP - 1_000)
        );

        given(upbitTickerStore.fetch(
                eq(CODE),
                eq(TIMESTAMP - Duration.ofMinutes(3).toMillis()),
                eq(TIMESTAMP)
        )).willReturn(iterator);

        // when
        sut.process(record);

        // then
        verify(upbitTickerStore).put(
                eq(CODE),
                eq(new UpbitTickerValue(110.0, TIMESTAMP)),
                eq(TIMESTAMP)
        );

        ArgumentCaptor<Record<String, PriceAlertDetectedEvent>> recordCaptor =
                ArgumentCaptor.forClass(Record.class);
        verify(context, times(3)).forward(recordCaptor.capture());

        List<PriceAlertDetectedEvent> events = recordCaptor.getAllValues()
                .stream()
                .map(Record::value)
                .toList();

        assertThat(recordCaptor.getAllValues())
                .extracting(Record::key)
                .containsOnly(CODE);

        assertThat(events)
                .extracting(PriceAlertDetectedEvent::getPartitionKey)
                .containsOnly(CODE);

        assertThat(events)
                .extracting(PriceAlertDetectedEvent::threshold)
                .containsExactlyInAnyOrder(
                        "PERCENT_3",
                        "PERCENT_5",
                        "PERCENT_7"
                );

        for (PriceAlertDetectedEvent detectedEvent : events) {
            assertThat(detectedEvent.code()).isEqualTo(CODE);
            assertThat(detectedEvent.price()).isEqualTo(110.0);
            assertThat(detectedEvent.timestamp()).isEqualTo(TIMESTAMP);
            assertThat(detectedEvent.avgInterval()).isEqualTo(3);
            assertThat(detectedEvent.avgPrice()).isEqualTo(100.0);
            assertThat(detectedEvent.changeRate()).isEqualTo(0.1);
        }
    }

    @Test
    @DisplayName("과거 가격 데이터가 없으면 현재가를 평균값으로 사용한다")
    void process_emptyWindowStore_useCurrentPriceAsAverage() {
        // given
        UpbitTickerEvent event = tickerEvent(110.0);
        Record<String, UpbitTickerEvent> record = new Record<>(CODE, event, TIMESTAMP);

        WindowStoreIterator<UpbitTickerValue> iterator = mockTickerIterator();

        given(upbitTickerStore.fetch(
                eq(CODE),
                eq(TIMESTAMP - Duration.ofMinutes(3).toMillis()),
                eq(TIMESTAMP)
        )).willReturn(iterator);

        // when
        sut.process(record);

        // then
        verify(upbitTickerStore).put(
                eq(CODE),
                eq(new UpbitTickerValue(110.0, TIMESTAMP)),
                eq(TIMESTAMP)
        );

        verify(context, never()).forward(any());
    }

    @Test
    @DisplayName("record key나 value가 null이면 아무것도 하지 않는다")
    void process_nullRecordData_doNothing() {
        // given
        Record<String, UpbitTickerEvent> nullKeyRecord =
                new Record<>(null, tickerEvent(100.0), TIMESTAMP);

        Record<String, UpbitTickerEvent> nullValueRecord =
                new Record<>(CODE, null, TIMESTAMP);

        // when
        sut.process(nullKeyRecord);
        sut.process(nullValueRecord);

        // then
        verifyNoInteractions(upbitTickerStore);
        verify(context, never()).forward(any());
    }

    @SuppressWarnings("unchecked")
    private WindowStoreIterator<UpbitTickerValue> mockTickerIterator(UpbitTickerValue... values) {
        WindowStoreIterator<UpbitTickerValue> iterator = mock(WindowStoreIterator.class);

        Boolean[] hasNextResults = new Boolean[values.length + 1];

        for (int i = 0; i < values.length; i++) {
            hasNextResults[i] = true;
        }

        hasNextResults[values.length] = false;

        given(iterator.hasNext()).willReturn(
                hasNextResults[0],
                List.of(hasNextResults).subList(1, hasNextResults.length).toArray(new Boolean[0])
        );

        if (values.length > 0) {
            KeyValue<Long, UpbitTickerValue>[] keyValues = new KeyValue[values.length];

            for (int i = 0; i < values.length; i++) {
                keyValues[i] = KeyValue.pair(TIMESTAMP - values.length + i, values[i]);
            }

            given(iterator.next()).willReturn(
                    keyValues[0],
                    List.of(keyValues).subList(1, keyValues.length).toArray(new KeyValue[0])
            );
        }

        return iterator;
    }

    private UpbitProperties createProperties() {
        return new UpbitProperties(
                new UpbitProperties.Websocket(
                        "wss://api.upbit.com/websocket/v1",
                        "test",
                        Duration.ofSeconds(3),
                        100
                ),
                new UpbitProperties.Ticker(
                        new UpbitProperties.Ticker.Alert(
                                3
                        )
                ),
                new UpbitProperties.Store(
                        new UpbitProperties.Store.StoreTicker(
                                STORE_NAME,
                                Duration.ofMinutes(3),
                                Duration.ofMinutes(3),
                                false
                        )
                )
        );
    }

    private UpbitTickerEvent tickerEvent(Double tradePrice) {
        return new UpbitTickerEvent(
                "ticker",
                UpbitTickerProcessorTest.CODE,
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
}
