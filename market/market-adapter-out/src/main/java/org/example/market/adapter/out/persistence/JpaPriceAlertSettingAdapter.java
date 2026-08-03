package org.example.market.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.example.market.application.port.out.PriceAlertSettingPersistencePort;
import org.example.market.application.service.command.ChangePriceAlertSettingsCommand.CreatePriceAlertSettingCommand;
import org.example.market.application.service.command.ChangePriceAlertSettingsCommand.UpdatePriceAlertSettingCommand;
import org.example.market.domain.model.PriceAlertSetting;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class JpaPriceAlertSettingAdapter implements PriceAlertSettingPersistencePort {

    private final JpaPriceAlertSettingRepository priceAlertSettingRepository;
    private final JpaMarketRepository marketRepository;

    @Override
    public List<PriceAlertSetting> findAllByUserPublicId(UUID userPublicId) {
        return priceAlertSettingRepository.findAllByUserPublicIdWithMarket(userPublicId)
                .stream()
                .map(JpaPriceAlertSetting::toDomain)
                .toList();
    }

    @Override
    public List<UUID> findReceiverIds(String marketCode, BigDecimal targetChangeRate) {
        if (marketCode == null || marketCode.isBlank() || targetChangeRate == null) {
            return List.of();
        }

        return priceAlertSettingRepository.findReceiverIdsByMarketCodeAndTargetChangeRate(marketCode, targetChangeRate);
    }

    @Override
    public void deleteSettingsByCodes(UUID userPublicId, List<String> codes) {
        Map<String, JpaPriceAlertSetting> existingSettingMap = findExistingSettingMap(userPublicId, Set.copyOf(codes));

        List<Long> ids = codes.stream()
                .map(existingSettingMap::get)
                .filter(Objects::nonNull)
                .map(JpaPriceAlertSetting::getId)
                .toList();

        if (ids.isEmpty()) {
            return;
        }

        priceAlertSettingRepository.deleteAllByIdInBatch(ids);
    }

    @Override
    public void updateSettings(UUID userPublicId, List<UpdatePriceAlertSettingCommand> commands) {
        Set<String> codes = commands.stream()
                .map(UpdatePriceAlertSettingCommand::code)
                .collect(Collectors.toSet());

        Map<String, JpaMarket> enabledMarketMap = findEnabledMarketMap(codes);
        Map<String, JpaPriceAlertSetting> existingSettingMap = findExistingSettingMap(userPublicId, codes);

        for (UpdatePriceAlertSettingCommand command : commands) {
            if (!enabledMarketMap.containsKey(command.code())) {
                continue;
            }

            JpaPriceAlertSetting setting = existingSettingMap.get(command.code());

            if (setting == null) {
                continue;
            }

            setting.update(command.enabled(), command.targetChangeRate());
        }
    }

    @Override
    public void createSettings(UUID userPublicId, List<CreatePriceAlertSettingCommand> commands) {
        Set<String> codes = commands.stream()
                .map(CreatePriceAlertSettingCommand::code)
                .collect(Collectors.toSet());

        Map<String, JpaMarket> enabledMarketMap = findEnabledMarketMap(codes);
        Map<String, JpaPriceAlertSetting> existingSettingMap = findExistingSettingMap(userPublicId, codes);

        List<JpaPriceAlertSetting> jpaSettings = new ArrayList<>();

        for (CreatePriceAlertSettingCommand command : commands) {
            JpaMarket market = enabledMarketMap.get(command.code());

            if (market == null) {
                continue;
            }

            if (existingSettingMap.containsKey(command.code())) {
                continue;
            }

            JpaPriceAlertSetting entity = JpaPriceAlertSetting.create(
                    userPublicId,
                    market,
                    command.enabled(),
                    command.targetChangeRate()
            );

            jpaSettings.add(entity);
        }

        if (jpaSettings.isEmpty()) {
            return;
        }

        priceAlertSettingRepository.saveAll(jpaSettings);
    }

    private Map<String, JpaMarket> findEnabledMarketMap(Set<String> marketCodes) {
        if (marketCodes.isEmpty()) {
            return Map.of();
        }

        return marketRepository.findAllByMarketCodeInAndEnabledTrue(marketCodes)
                .stream()
                .collect(Collectors.toMap(
                        JpaMarket::getMarketCode,
                        Function.identity()
                ));
    }

    private Map<String, JpaPriceAlertSetting> findExistingSettingMap(UUID userPublicId, Set<String> marketCodes) {
        if (marketCodes.isEmpty()) {
            return Map.of();
        }

        return priceAlertSettingRepository.findAllByUserPublicIdAndMarketCodeIn(userPublicId, marketCodes
                )
                .stream()
                .collect(Collectors.toMap(
                        setting -> setting.getMarket().getMarketCode(),
                        Function.identity()
                ));
    }
}