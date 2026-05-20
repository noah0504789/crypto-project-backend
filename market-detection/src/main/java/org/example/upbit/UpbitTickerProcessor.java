package org.example.upbit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.example.common.event.notification.WebNotificationEvent;
import org.example.upbit.event.UpbitTickerAlertEvent;
import org.example.upbit.event.UpbitTickerEvent;
import org.example.upbit.event.UpbitTickerValue;
import org.example.infra.properties.UpbitProperties;
import org.springframework.cloud.stream.function.StreamBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class UpbitTickerProcessor implements Processor<String, UpbitTickerEvent, Void, Void> {

    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;
    private final UpbitProperties properties;

    private WindowStore<String, UpbitTickerValue> upbitTickerStore;

    @Override
    public void init(ProcessorContext<Void, Void> context) {
        String name = properties.store().ticker().name();

        this.upbitTickerStore = context.getStateStore(name);
    }

    @Override
    public void process(Record<String, UpbitTickerEvent> record) {
        if (record.key() == null || record.value() == null) {
            return;
        }

        String code = record.key();
        UpbitTickerEvent tickerEvent = record.value();

        if (tickerEvent.tradePrice() == null) {
            return;
        }

        long timestamp = record.timestamp();
        double currentPrice = tickerEvent.tradePrice();

        double averagePrice = calculateAveragePrice(code, timestamp, currentPrice);
        double changeRate = calculateChangeRate(currentPrice, averagePrice);

        saveCurrentTicker(code, currentPrice, timestamp);

        if (isBelowThreshold(changeRate)) {
            return;
        }

        UpbitTickerAlertEvent alertEvent = createAlertEvent(
                code,
                currentPrice,
                timestamp,
                averagePrice,
                changeRate
        );

        publishNotification(alertEvent);
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

    private boolean isBelowThreshold(double changeRate) {
        return Math.abs(changeRate) <= properties.ticker().alert().thresholdRate();
    }

    private UpbitTickerAlertEvent createAlertEvent(
            String code,
            double currentPrice,
            long timestamp,
            double averagePrice,
            double changeRate
    ) {
        return new UpbitTickerAlertEvent(
                code,
                currentPrice,
                timestamp,
                properties.ticker().alert().windowMinutes(),
                averagePrice,
                changeRate
        );
    }

    private void publishNotification(UpbitTickerAlertEvent event) {
        Map<String, Object> payload = objectMapper.convertValue(
                event,
                new TypeReference<Map<String, Object>>() {}
        );

        WebNotificationEvent notification = new WebNotificationEvent(
                event.getClass().getSimpleName(),
                payload,
                "code"
        );

        streamBridge.send(
                notification.getTopic().getBindingName(),
                notification.toMessage()
        );
    }

    private long getWindowStartTime(long timestamp) {
        return timestamp - properties.ticker().alert().windowDuration().toMillis();
    }
}