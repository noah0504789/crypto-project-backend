package org.example.marketdetection.contract.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.example.common.enums.KafkaTopic;
import org.example.common.event.KafkaEvent;
import org.example.common.event.ProducibleEvent;
import org.example.common.event.TypedPayload;
import org.example.common.inbox.domain.event.AbstractInboxEvent;

@Getter
@ToString
public final class PriceAlertDetectedEvent extends AbstractInboxEvent implements KafkaEvent, ProducibleEvent {

    private final String code;
    private final Double price;
    private final Long timestamp;
    private final Integer avgInterval;
    private final Double avgPrice;
    private final Double changeRate;
    private final String threshold;

    @Builder
    @JsonCreator
    public PriceAlertDetectedEvent(
            @JsonProperty("code") String code,
            @JsonProperty("price") Double price,
            @JsonProperty("timestamp") Long timestamp,
            @JsonProperty("avgInterval") Integer avgInterval,
            @JsonProperty("avgPrice") Double avgPrice,
            @JsonProperty("changeRate") Double changeRate,
            @JsonProperty("threshold") String threshold
    ) {
        this.code = code;
        this.price = price;
        this.timestamp = timestamp;
        this.avgInterval = avgInterval;
        this.avgPrice = avgPrice;
        this.changeRate = changeRate;
        this.threshold = threshold;
    }

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
                .put(PriceAlertDetectedPayloadKeys.OCCURRED_AT_MS, timestamp)
                .put(PriceAlertDetectedPayloadKeys.AVG_INTERVAL, avgInterval)
                .put(PriceAlertDetectedPayloadKeys.AVG_PRICE, avgPrice)
                .put(PriceAlertDetectedPayloadKeys.CHANGE_RATE, changeRate)
                .put(PriceAlertDetectedPayloadKeys.THRESHOLD, threshold)
                .build();
    }
}
