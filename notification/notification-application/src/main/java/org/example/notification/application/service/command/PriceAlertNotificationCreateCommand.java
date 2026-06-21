package org.example.notification.application.service.command;

import lombok.Builder;
import org.example.common.event.TypedPayload;

import java.util.Map;

@Builder
public record PriceAlertNotificationCreateCommand(
        String code,
        Double price,
        Long timestamp,
        Integer avgInterval,
        Double avgPrice,
        Double changeRate,
        String threshold,
        TypedPayload typedPayload,
        String transactionId
) {

    public PriceAlertNotificationCreateCommand {
        typedPayload = typedPayload == null ? TypedPayload.empty() : typedPayload;
    }

    public Map<String, Object> toPayload() {
        return typedPayload.toMap();
    }

    public String routingKey() {
        return "price-alert/%s/%s".formatted(code, threshold);
    }
}