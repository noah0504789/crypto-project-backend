package org.example.marketdetection.application.dto;

import java.util.List;
import org.example.common.enums.PriceAlertChangeRateThreshold;

/**
 * 단기 이동평균 대비 변동률과 임계 매칭. 저장소·메시징을 모르는 순수 계산이다.
 */
public record PriceChange(double currentPrice, double averagePrice, double changeRate) {

    public static PriceChange of(double currentPrice, List<PricePoint> points) {
        double averagePrice = averageOf(points, currentPrice);

        return new PriceChange(currentPrice, averagePrice, changeRateOf(currentPrice, averagePrice));
    }

    public List<PriceAlertChangeRateThreshold> matchedThresholds() {
        return PriceAlertChangeRateThreshold.matchedBy(changeRate);
    }

    private static double averageOf(List<PricePoint> points, double fallbackPrice) {
        return points.stream()
                .filter(pricePoint -> pricePoint != null && pricePoint.price() != null)
                .mapToDouble(PricePoint::price)
                .average()
                .orElse(fallbackPrice);
    }

    private static double changeRateOf(double currentPrice, double averagePrice) {
        if (averagePrice == 0) {
            return 0;
        }

        return (currentPrice - averagePrice) / averagePrice;
    }
}
