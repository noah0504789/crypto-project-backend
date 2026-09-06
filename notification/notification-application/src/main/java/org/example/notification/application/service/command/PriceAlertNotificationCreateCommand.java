package org.example.notification.application.service.command;

import lombok.Builder;
import org.example.common.event.TypedPayload;
import org.example.notification.contract.event.PriceAlertPayload;

import java.util.Map;

@Builder
public record PriceAlertNotificationCreateCommand(
        String eventId,
        String code,
        Double price,
        Long occurredAtMs,
        Integer avgInterval,
        Double avgPrice,
        Double changeRate,
        String threshold,
        TypedPayload typedPayload,
        String transactionId
) {

    public static final String CONSUMER_NAME = "notification.price-alert-detected";

    public PriceAlertNotificationCreateCommand {
        typedPayload = typedPayload == null ? TypedPayload.empty() : typedPayload;
    }

    public Map<String, Object> toPayload() {
        return typedPayload.toMap();
    }

    public PriceAlertPayload toPriceAlertPayload() {
        return new PriceAlertPayload(code, price, avgPrice, avgInterval, changeRate, threshold, occurredAtMs);
    }

    public String consumerName() {
        return CONSUMER_NAME;
    }

    public String partitionKey() {
        return "price-alert/%s/%s".formatted(code, threshold);
    }
}
