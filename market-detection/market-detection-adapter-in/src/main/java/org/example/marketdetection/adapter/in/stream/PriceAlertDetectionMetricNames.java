package org.example.marketdetection.adapter.in.stream;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PriceAlertDetectionMetricNames {

    public static final String TIMESTAMP_FALLBACK = "price.alert.detection.timestamp.fallback";
    public static final String TICKER_REJECTED = "price.alert.detection.ticker.rejected";
    public static final String TICKER_STALE = "price.alert.detection.ticker.stale";
}
