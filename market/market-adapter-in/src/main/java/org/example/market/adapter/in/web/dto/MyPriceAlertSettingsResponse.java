package org.example.market.adapter.in.web.dto;

import org.example.market.application.service.result.MyPriceAlertSettingResult;

import java.math.BigDecimal;
import java.util.List;

public record MyPriceAlertSettingsResponse(
        List<MyPriceAlertSettingResponse> settings
) {

    public static MyPriceAlertSettingsResponse from(List<MyPriceAlertSettingResult> results) {
        return new MyPriceAlertSettingsResponse(
                results.stream().map(MyPriceAlertSettingResponse::from).toList()
        );
    }

    public record MyPriceAlertSettingResponse(
            String code,
            boolean enabled,
            BigDecimal targetChangeRate
    ) {

        public static MyPriceAlertSettingResponse from(MyPriceAlertSettingResult result) {
            return new MyPriceAlertSettingResponse(result.code(), result.enabled(), result.targetChangeRate());
        }
    }
}