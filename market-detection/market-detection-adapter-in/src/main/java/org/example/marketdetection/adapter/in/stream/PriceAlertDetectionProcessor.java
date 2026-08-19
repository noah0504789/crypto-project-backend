package org.example.marketdetection.adapter.in.stream;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.WindowStoreIterator;
import org.example.common.enums.KafkaHeaderKey;
import org.example.marketdetection.application.port.in.PriceAlertDetectUseCase;
import org.example.marketdetection.application.properties.PriceAlertDetectionProperties;
import org.example.marketdetection.contract.event.PriceAlertDetectedEvent;
import org.example.marketdetection.application.dto.PricePoint;
import org.example.upbitconnector.contract.event.UpbitTickerEvent;

/** Kafka Streams 글루. 상태 저장소 접근과 결과 forward 만 하고, 탐지 판단은 application 에 맡긴다. */
@RequiredArgsConstructor
public class PriceAlertDetectionProcessor implements Processor<String, UpbitTickerEvent, String, PriceAlertDetectedEvent> {

    private final PriceAlertDetectUseCase priceAlertDetectUseCase;
    private final PriceAlertDetectionProperties properties;

    private ProcessorContext<String, PriceAlertDetectedEvent> context;
    private WindowStore<String, PricePoint> pricePointStore;

    @Override
    public void init(ProcessorContext<String, PriceAlertDetectedEvent> context) {
        this.context = context;
        this.pricePointStore = context.getStateStore(properties.store().name());
    }

    @Override
    public void process(Record<String, UpbitTickerEvent> record) {
        if (!isProcessable(record)) {
            return;
        }

        String code = record.key();
        PricePoint pricePoint = toPricePoint(record);

        if (priceAlertDetectUseCase.isStale(pricePoint)) {
            return;
        }

        List<PricePoint> recentPoints = fetchRecentPoints(code, pricePoint.timestamp());

        pricePointStore.put(code, pricePoint, pricePoint.timestamp());

        priceAlertDetectUseCase.detect(code, pricePoint, recentPoints)
                .forEach(event -> forward(record, code, pricePoint, event));
    }

    private boolean isProcessable(Record<String, UpbitTickerEvent> record) {
        return record != null
                && record.key() != null
                && !record.key().isBlank()
                && record.value() != null
                && record.value().tradePrice() != null;
    }

    private PricePoint toPricePoint(Record<String, UpbitTickerEvent> record) {
        UpbitTickerEvent tickerEvent = record.value();
        Long timestamp = tickerEvent.tradeTimestamp() != null
                        ? tickerEvent.tradeTimestamp()
                        : record.timestamp();

        return new PricePoint(tickerEvent.tradePrice(), timestamp);
    }

    private List<PricePoint> fetchRecentPoints(String code, long timestamp) {
        long from = timestamp - properties.windowDuration().toMillis();
        List<PricePoint> points = new ArrayList<>();

        try (WindowStoreIterator<PricePoint> iterator = pricePointStore.fetch(code, from, timestamp)) {
            while (iterator.hasNext()) {
                points.add(iterator.next().value);
            }
        }

        return points;
    }

    private void forward(
            Record<String, UpbitTickerEvent> record,
            String code,
            PricePoint pricePoint,
            PriceAlertDetectedEvent event) {
        RecordHeaders headers = new RecordHeaders(record.headers());
        headers.add(
                KafkaHeaderKey.EVENT_ID.value(),
                event.getEventId().getBytes(StandardCharsets.UTF_8)
        );

        context.forward(new Record<>(code, event, pricePoint.timestamp(), headers));
    }
}
