package org.example.marketdetection.application.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.common.enums.PriceAlertChangeRateThreshold;
import org.example.common.time.Clock;
import org.example.marketdetection.application.port.in.PriceAlertDetectUseCase;
import org.example.marketdetection.application.properties.PriceAlertDetectionProperties;
import org.example.marketdetection.contract.event.PriceAlertDetectedEvent;
import org.example.marketdetection.application.dto.PriceChange;
import org.example.marketdetection.application.dto.PricePoint;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PriceAlertDetectionService implements PriceAlertDetectUseCase {

    private final PriceAlertDetectionProperties properties;
    private final Clock clock;

    @Override
    public List<PriceAlertDetectedEvent> detect(String code, PricePoint pricePoint, List<PricePoint> recentPoints) {
        PriceChange priceChange = PriceChange.of(pricePoint.price(), recentPoints);

        return priceChange.matchedThresholds().stream()
                .map(threshold -> toEvent(code, pricePoint, priceChange, threshold))
                .toList();
    }

    @Override
    public boolean isStale(long staleCheckMs) {
        return clock.nowMs() - staleCheckMs > properties.maxEventAge().toMillis();
    }

    private PriceAlertDetectedEvent toEvent(
            String code,
            PricePoint pricePoint,
            PriceChange priceChange,
            PriceAlertChangeRateThreshold threshold) {
        return PriceAlertDetectedEvent.builder()
                .code(code)
                .price(priceChange.currentPrice())
                .timestamp(pricePoint.timestamp())
                .avgInterval(properties.windowMinutes())
                .avgPrice(priceChange.averagePrice())
                .changeRate(priceChange.changeRate())
                .threshold(threshold.name())
                .build();
    }
}
