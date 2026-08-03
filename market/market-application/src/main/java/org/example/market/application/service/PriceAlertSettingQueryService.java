package org.example.market.application.service;

import lombok.RequiredArgsConstructor;
import org.example.market.application.port.in.PriceAlertSettingQueryUseCase;
import org.example.market.application.port.out.MarketPersistencePort;
import org.example.market.application.port.out.PriceAlertSettingPersistencePort;
import org.example.market.application.service.result.MyPriceAlertSettingResult;
import org.example.market.domain.model.Market;
import org.example.market.domain.model.PriceAlertSetting;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PriceAlertSettingQueryService implements PriceAlertSettingQueryUseCase {

    private final PriceAlertSettingPersistencePort priceAlertSettingPersistencePort;
    private final MarketPersistencePort marketPersistencePort;

    @Override
    public List<MyPriceAlertSettingResult> getMySettings(UUID userPublicId) {
        List<PriceAlertSetting> settings = priceAlertSettingPersistencePort.findAllByUserPublicId(userPublicId);

        if (settings.isEmpty()) {
            return List.of();
        }

        Set<Long> marketIds = settings.stream()
                .map(PriceAlertSetting::getMarketId)
                .collect(Collectors.toSet());

        Map<Long, Market> enabledMarketMap = marketPersistencePort.findAllEnabledByIds(marketIds)
                .stream()
                .collect(Collectors.toMap(
                        Market::getId,
                        Function.identity()
                ));

        return settings.stream()
                .filter(setting -> enabledMarketMap.containsKey(setting.getMarketId()))
                .map(setting -> {
                    Market market = enabledMarketMap.get(setting.getMarketId());

                    return new MyPriceAlertSettingResult(
                            market.getMarketCode(),
                            setting.isEnabled(),
                            setting.getTargetChangeRate()
                    );
                })
                .toList();
    }

    @Override
    public List<UUID> findReceiverIds(String marketCode, BigDecimal targetChangeRate) {
        if (marketCode == null || marketCode.isBlank() || targetChangeRate == null) {
            return List.of();
        }

        return priceAlertSettingPersistencePort.findReceiverIds(marketCode, targetChangeRate);
    }
}