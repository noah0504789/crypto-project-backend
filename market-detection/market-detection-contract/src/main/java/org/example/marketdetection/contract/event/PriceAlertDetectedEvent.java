package org.example.marketdetection.contract.event;

import org.example.common.enums.KafkaTopic;
import org.example.common.event.KafkaEvent;
import org.example.common.event.ProducibleEvent;

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
}