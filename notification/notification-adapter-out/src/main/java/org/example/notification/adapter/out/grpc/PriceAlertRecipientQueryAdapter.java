package org.example.notification.adapter.out.grpc;

import lombok.RequiredArgsConstructor;
import org.example.common.enums.PriceAlertChangeRateThreshold;
import org.example.market.client.PriceAlertSettingClient;
import org.example.notification.application.port.out.PriceAlertRecipientQueryPort;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PriceAlertRecipientQueryAdapter implements PriceAlertRecipientQueryPort {

    private final PriceAlertSettingClient priceAlertSettingClient;

    @Override
    public List<UUID> findReceiverIds(String marketCode, String threshold) {
        if (marketCode == null || marketCode.isBlank() || threshold == null || threshold.isBlank()) {
            return List.of();
        }

        BigDecimal targetChangeRate = PriceAlertChangeRateThreshold.toBigDecimal(threshold);

        return priceAlertSettingClient.findReceiverIds(marketCode, targetChangeRate);
    }
}