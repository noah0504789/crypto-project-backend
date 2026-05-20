package org.example.upbit.event;

import org.example.common.event.KafkaEvent;

public record UpbitTickerAlertEvent(
        String code,
        Double price,
        Long timestamp,
        Integer avgInterval,
        Double avgPrice,
        Double changeRate
) implements KafkaEvent {

    @Override
    public String getPartitionKey() {
        if (code == null) {
            throw new IllegalStateException("UpbitTickerAlertEvent code is null");
        }

        return code;
    }
}