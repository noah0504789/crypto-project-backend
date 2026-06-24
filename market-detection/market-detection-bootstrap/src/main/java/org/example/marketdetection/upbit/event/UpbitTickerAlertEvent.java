package org.example.marketdetection.upbit.event;

import org.example.common.event.KafkaEvent;
import org.example.market.client.PriceAlertChangeRateThreshold;

public record UpbitTickerAlertEvent(
        String code,
        Double price,
        Long timestamp,
        Integer avgInterval,
        Double avgPrice,
        Double changeRate,
        PriceAlertChangeRateThreshold matchedChangeRateThreshold
) implements KafkaEvent {

    @Override
    public String getPartitionKey() {
        if (code == null) {
            throw new IllegalStateException("UpbitTickerAlertEvent code is null");
        }

        return code;
    }
}