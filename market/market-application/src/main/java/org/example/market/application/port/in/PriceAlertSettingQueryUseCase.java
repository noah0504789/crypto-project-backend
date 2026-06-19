package org.example.market.application.port.in;

import org.example.market.application.service.result.MyPriceAlertSettingResult;

import java.util.List;
import java.util.UUID;

public interface PriceAlertSettingQueryUseCase {

    List<MyPriceAlertSettingResult> getMySettings(UUID userPublicId);
}