package org.example.marketdetection.contract.event;

import lombok.Builder;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.KafkaEvent;
import org.example.common.event.ProducibleEvent;
import org.example.common.event.TypedPayload;

@Builder
public record PriceAlertDetectedEvent(
        String code,
        Double price,
        Long timestamp,
        Integer avgInterval,
        Double avgPrice,
        Double changeRate,
        String threshold
) implements KafkaEvent, ProducibleEvent {

    @Override
    public String getPartitionKey() {
        if (code == null || code.isBlank()) {
            throw new IllegalStateException("Price alert detected event code is missing.");
        }

        return code;
    }

    @Override
    public KafkaTopic getTopic() {
        return KafkaTopic.PRICE_ALERT_DETECTED;
    }

    public TypedPayload toPayload() {
        return TypedPayload.builder()
                .put(PriceAlertDetectedPayloadKeys.CODE, code)
                .put(PriceAlertDetectedPayloadKeys.PRICE, price)
                .put(PriceAlertDetectedPayloadKeys.TIMESTAMP, timestamp)
                .put(PriceAlertDetectedPayloadKeys.AVG_INTERVAL, avgInterval)
                .put(PriceAlertDetectedPayloadKeys.AVG_PRICE, avgPrice)
                .put(PriceAlertDetectedPayloadKeys.CHANGE_RATE, changeRate)
                .put(PriceAlertDetectedPayloadKeys.THRESHOLD, threshold)
                .build();
    }
}