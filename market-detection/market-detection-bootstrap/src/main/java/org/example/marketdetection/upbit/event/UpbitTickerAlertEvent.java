package org.example.marketdetection.upbit.event;

import org.example.common.event.KafkaEvent;
import org.example.common.event.TypedPayload;
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

    public TypedPayload toPayload() {
        return TypedPayload.builder()
                .put(UpbitTickerAlertPayloadKeys.CODE, code)
                .put(UpbitTickerAlertPayloadKeys.PRICE, price)
                .put(UpbitTickerAlertPayloadKeys.TIMESTAMP, timestamp)
                .put(UpbitTickerAlertPayloadKeys.AVG_INTERVAL, avgInterval)
                .put(UpbitTickerAlertPayloadKeys.AVG_PRICE, avgPrice)
                .put(UpbitTickerAlertPayloadKeys.CHANGE_RATE, changeRate)
                .put(UpbitTickerAlertPayloadKeys.MATCHED_CHANGE_RATE_THRESHOLD, matchedChangeRateThreshold.name())
                .build();
    }
}