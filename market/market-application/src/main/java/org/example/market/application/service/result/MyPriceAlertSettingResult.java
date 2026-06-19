package org.example.market.application.service.result;

import java.math.BigDecimal;

public record MyPriceAlertSettingResult(
        String code,
        boolean enabled,
        BigDecimal targetChangeRate
) {
}