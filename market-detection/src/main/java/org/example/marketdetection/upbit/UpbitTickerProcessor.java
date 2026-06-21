package org.example.marketdetection.upbit;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.example.common.event.notification.WebNotificationEvent;
import org.example.market.client.PriceAlertChangeRateThreshold;
import org.example.marketdetection.upbit.event.UpbitTickerAlertEvent;
import org.example.marketdetection.upbit.event.UpbitTickerEvent;
import org.example.marketdetection.upbit.event.UpbitTickerValue;
import org.example.marketdetection.infra.properties.UpbitProperties;
import org.springframework.cloud.stream.function.StreamBridge;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class UpbitTickerProcessor implements Processor<String, UpbitTickerEvent, Void, Void> {

    private final StreamBridge streamBridge;
    private final UpbitProperties properties;

    private WindowStore<String, UpbitTickerValue> upbitTickerStore;

    @Override
    public void init(ProcessorContext<Void, Void> context) {
        this.upbitTickerStore = context.getStateStore(properties.store().ticker().name());
    }

    @Override
    public void process(Record<String, UpbitTickerEvent> record) {
        if (!isProcessable(record)) {
            return;
        }

        String code = record.key();
        UpbitTickerEvent tickerEvent = record.value();

        long timestamp = record.timestamp();
        double currentPrice = tickerEvent.tradePrice();

        double averagePrice = calculateAveragePrice(code, timestamp, currentPrice);
        double changeRate = calculateChangeRate(currentPrice, averagePrice);

        saveCurrentTicker(code, currentPrice, timestamp);

        List<PriceAlertChangeRateThreshold> matchedChangeRateThresholds = PriceAlertChangeRateThreshold.matchedBy(changeRate);

        if (matchedChangeRateThresholds.isEmpty()) {
            return;
        }

        for (PriceAlertChangeRateThreshold threshold : matchedChangeRateThresholds) {
            UpbitTickerAlertEvent event = createAlertEvent(
                    code,
                    currentPrice,
                    timestamp,
                    averagePrice,
                    changeRate,
                    threshold
            );

            publishNotification(event);
        }
    }

    private boolean isProcessable(Record<String, UpbitTickerEvent> record) {
        return record != null
                && record.key() != null
                && !record.key().isBlank()
                && record.value() != null
                && record.value().tradePrice() != null;
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

    private UpbitTickerAlertEvent createAlertEvent(
            String code,
            double currentPrice,
            long timestamp,
            double averagePrice,
            double changeRate,
            PriceAlertChangeRateThreshold matchedChangeRateThreshold
    ) {
        return new UpbitTickerAlertEvent(
                code,
                currentPrice,
                timestamp,
                properties.ticker().alert().windowMinutes(),
                averagePrice,
                changeRate,
                matchedChangeRateThreshold
        );
    }

    private void publishNotification(UpbitTickerAlertEvent event) {
        WebNotificationEvent notification = new WebNotificationEvent(
                event.getClass().getSimpleName(),
                event.toPayload(),
                createRoutingKey(event)
        );

        streamBridge.send(notification.getTopic().getBindingName(), notification.toMessage());
    }

    private String createRoutingKey(UpbitTickerAlertEvent event) {
        return "price-alert/%s/%s".formatted(event.code(), event.matchedChangeRateThreshold().name());
    }

    private long getWindowStartTime(long timestamp) {
        return timestamp - properties.ticker().alert().windowDuration().toMillis();
    }
}