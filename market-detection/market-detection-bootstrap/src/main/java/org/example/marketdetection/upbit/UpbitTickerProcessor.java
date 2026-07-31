package org.example.marketdetection.upbit;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.example.common.clock.Clock;
import org.example.common.enums.KafkaHeaderKey;
import org.example.common.enums.PriceAlertChangeRateThreshold;
import org.example.marketdetection.contract.event.PriceAlertDetectedEvent;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.example.marketdetection.upbit.event.UpbitTickerEvent;
import org.example.marketdetection.upbit.event.UpbitTickerValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class UpbitTickerProcessor implements Processor<String, UpbitTickerEvent, String, PriceAlertDetectedEvent> {

    private final UpbitProperties properties;
    private final Clock clock;

    private ProcessorContext<String, PriceAlertDetectedEvent> context;
    private WindowStore<String, UpbitTickerValue> upbitTickerStore;

    @Override
    public void init(ProcessorContext<String, PriceAlertDetectedEvent> context) {
        this.context = context;
        this.upbitTickerStore = context.getStateStore(properties.store().ticker().name());
    }

    @Override
    public void process(Record<String, UpbitTickerEvent> record) {
        if (!isProcessable(record)) {
            return;
        }

        String code = record.key();
        UpbitTickerEvent tickerEvent = record.value();

        if (isStale(tickerEvent, record.timestamp())) {
            return;
        }

        long timestamp = record.timestamp();
        double currentPrice = tickerEvent.tradePrice();

        double averagePrice = calculateAveragePrice(code, timestamp, currentPrice);
        double changeRate = calculateChangeRate(currentPrice, averagePrice);

        saveCurrentTicker(code, currentPrice, timestamp);

        List<PriceAlertChangeRateThreshold> matchedChangeRateThresholds = PriceAlertChangeRateThreshold.matchedBy(changeRate);

        if (matchedChangeRateThresholds.isEmpty()) {
            return;
        }

        matchedChangeRateThresholds.forEach(threshold -> {
            PriceAlertDetectedEvent event =
                    createPriceAlertDetectedEvent(code, currentPrice, timestamp, averagePrice, changeRate, threshold);

            RecordHeaders headers = new RecordHeaders(record.headers());
            headers.add(
                    KafkaHeaderKey.EVENT_ID.value(),
                    event.getEventId().getBytes(StandardCharsets.UTF_8)
            );

            context.forward(new Record<>(code, event, timestamp, headers));
        });
    }

    private boolean isProcessable(Record<String, UpbitTickerEvent> record) {
        return record != null
                && record.key() != null
                && !record.key().isBlank()
                && record.value() != null
                && record.value().tradePrice() != null;
    }

    private boolean isStale(UpbitTickerEvent tickerEvent, long recordTimestamp) {
        long eventTimestamp = tickerEvent.tradeTimestamp() != null
                ? tickerEvent.tradeTimestamp()
                : recordTimestamp;

        return clock.nowMs() - eventTimestamp > properties.ticker().alert().maxEventAge().toMillis();
    }

    private double calculateAveragePrice(String code, long timestamp, double fallbackPrice) {
        long from = getWindowStartTime(timestamp);
        List<Double> prices = new ArrayList<>();

        try (WindowStoreIterator<UpbitTickerValue> iterator = upbitTickerStore.fetch(code, from, timestamp)) {
            while (iterator.hasNext()) {
                UpbitTickerValue tickerValue = iterator.next().value;

                if (tickerValue == null || tickerValue.price() == null) {
                    continue;
                }

                prices.add(tickerValue.price());
            }
        }

        return prices.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(fallbackPrice);
    }

    private double calculateChangeRate(double currentPrice, double averagePrice) {
        if (averagePrice == 0) {
            return 0;
        }

        return (currentPrice - averagePrice) / averagePrice;
    }

    private void saveCurrentTicker(String code, double currentPrice, long timestamp) {
        upbitTickerStore.put(code, new UpbitTickerValue(currentPrice, timestamp), timestamp);
    }

    private PriceAlertDetectedEvent createPriceAlertDetectedEvent(
            String code,
            double currentPrice,
            long timestamp,
            double averagePrice,
            double changeRate,
            PriceAlertChangeRateThreshold threshold
    ) {
        return PriceAlertDetectedEvent.builder()
                .code(code)
                .price(currentPrice)
                .timestamp(timestamp)
                .avgInterval(properties.ticker().alert().windowMinutes())
                .avgPrice(averagePrice)
                .changeRate(changeRate)
                .threshold(threshold.name())
                .build();
    }

    private long getWindowStartTime(long timestamp) {
        return timestamp - properties.ticker().alert().windowDuration().toMillis();
    }
}
