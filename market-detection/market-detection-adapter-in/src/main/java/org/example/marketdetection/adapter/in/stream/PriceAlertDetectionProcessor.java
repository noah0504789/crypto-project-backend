package org.example.marketdetection.adapter.in.stream;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class PriceAlertDetectionProcessor implements Processor<String, UpbitTickerEvent, String, PriceAlertDetectedEvent> {

    // "6종목 × 7초"라 매 폴백마다 찍으면 상시 로그가 된다. 첫 발생은 바로 알리고, 그 뒤로는
    // 주기적으로만 남겨 추세는 카운터로, 발생 사실은 로그로 각자 확인한다.
    private static final long FALLBACK_LOG_INTERVAL = 100L;

    private static final String REJECT_REASON_KEY = "key";
    private static final String REJECT_REASON_VALUE = "value";
    private static final String REJECT_REASON_TRADE_PRICE = "tradePrice";
    private static final List<String> REJECT_REASONS =
            List.of(REJECT_REASON_KEY, REJECT_REASON_VALUE, REJECT_REASON_TRADE_PRICE);

    private final PriceAlertDetectUseCase priceAlertDetectUseCase;
    private final PriceAlertDetectionProperties properties;
    private final Counter timestampFallbackCounter;
    private final Counter staleDiscardedCounter;
    private final Map<String, Counter> rejectedCounters;
    private final AtomicLong fallbackOccurrences = new AtomicLong();

    private ProcessorContext<String, PriceAlertDetectedEvent> context;
    private WindowStore<String, PricePoint> pricePointStore;

    public PriceAlertDetectionProcessor(
            PriceAlertDetectUseCase priceAlertDetectUseCase,
            PriceAlertDetectionProperties properties,
            MeterRegistry meterRegistry) {
        this.priceAlertDetectUseCase = priceAlertDetectUseCase;
        this.properties = properties;
        this.timestampFallbackCounter = Counter.builder(PriceAlertDetectionMetricNames.TIMESTAMP_FALLBACK)
                .description("tradeTimestamp 가 없어 record timestamp 로 stale 판정을 대체한 ticker 수")
                .register(meterRegistry);
        this.staleDiscardedCounter = Counter.builder(PriceAlertDetectionMetricNames.TICKER_STALE)
                .description("stale 판정으로 폐기한 ticker 수")
                .register(meterRegistry);
        this.rejectedCounters = REJECT_REASONS.stream()
                .collect(Collectors.toMap(
                        reason -> reason,
                        reason -> Counter.builder(PriceAlertDetectionMetricNames.TICKER_REJECTED)
                                .description("isProcessable 탈락으로 건너뛴 ticker 수")
                                .tag("reason", reason)
                                .register(meterRegistry)));
    }

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

        if (priceAlertDetectUseCase.isStale(staleCheckMs(record))) {
            staleDiscardedCounter.increment();
            return;
        }

        List<PricePoint> recentPoints = fetchRecentPoints(code, pricePoint.timestamp());

        pricePointStore.put(code, pricePoint, pricePoint.timestamp());

        priceAlertDetectUseCase.detect(code, pricePoint, recentPoints)
                .forEach(event -> forward(record, code, pricePoint, event));
    }

    private boolean isProcessable(Record<String, UpbitTickerEvent> record) {
        String reason = rejectReason(record);

        if (reason == null) {
            return true;
        }

        rejectedCounters.get(reason).increment();
        return false;
    }

    private String rejectReason(Record<String, UpbitTickerEvent> record) {
        if (record == null || record.value() == null) {
            return REJECT_REASON_VALUE;
        }
        if (record.key() == null || record.key().isBlank()) {
            return REJECT_REASON_KEY;
        }
        if (record.value().tradePrice() == null) {
            return REJECT_REASON_TRADE_PRICE;
        }

        return null;
    }

    private PricePoint toPricePoint(Record<String, UpbitTickerEvent> record) {
        return new PricePoint(record.value().tradePrice(), record.timestamp());
    }

    /** stale 판정의 age 를 재는 기준점. Event Time(체결 시각)이 없으면 CreateTime 으로 대체한다. */
    private long staleCheckMs(Record<String, UpbitTickerEvent> record) {
        Long tradeTimestamp = record.value().tradeTimestamp();

        if (tradeTimestamp != null) {
            return tradeTimestamp;
        }

        timestampFallbackCounter.increment();
        logTimestampFallback(record.key());

        return record.timestamp();
    }

    private void logTimestampFallback(String code) {
        long occurrences = fallbackOccurrences.incrementAndGet();

        if (occurrences == 1 || occurrences % FALLBACK_LOG_INTERVAL == 0) {
            log.warn(
                    "[price-alert] tradeTimestamp missing, stale check falls back to record CreateTime "
                            + "(producer publish time instead of trade time). code={}, occurrences={}",
                    code,
                    occurrences);
        }
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
                event.issueEventId().getBytes(StandardCharsets.UTF_8)
        );

        context.forward(new Record<>(code, event, pricePoint.timestamp(), headers));
    }
}
