package org.example.market.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.example.market.application.port.out.PriceAlertSettingPersistencePort;
import org.example.market.application.service.command.ChangePriceAlertSettingsCommand;
import org.example.market.application.service.command.ChangePriceAlertSettingsCommand.CreatePriceAlertSettingCommand;
import org.example.market.application.service.command.ChangePriceAlertSettingsCommand.UpdatePriceAlertSettingCommand;
import org.example.market.application.service.command.ChangePriceAlertSettingsCommand.DeletePriceAlertSettingCommand;
import org.example.market.client.PriceAlertChangeRateThreshold;
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
    public void changeSettings(UUID userPublicId, ChangePriceAlertSettingsCommand command) {
        if (command.isEmpty()) {
            return;
        }

        Set<String> enabledTargetCodes = collectEnabledTargetCodes(command);
        Map<String, JpaMarket> enabledMarketMap = findEnabledMarketMap(enabledTargetCodes);

        Set<String> settingTargetCodes = collectSettingTargetCodes(command);
        Map<String, JpaPriceAlertSetting> existingSettingMap = findExistingSettingMap(userPublicId, settingTargetCodes);

        deleteSettings(command.deletes(), existingSettingMap);
        updateSettings(command.updates(), enabledMarketMap, existingSettingMap);
        createSettings(userPublicId, command.creates(), enabledMarketMap, existingSettingMap);
    }

    private void createSettings(
            UUID userPublicId,
            List<CreatePriceAlertSettingCommand> commands,
            Map<String, JpaMarket> enabledMarketMap,
            Map<String, JpaPriceAlertSetting> existingSettingMap
    ) {
        if (commands.isEmpty()) {
            return;
        }

        List<JpaPriceAlertSetting> jpaSettings = new ArrayList<>();

        for (CreatePriceAlertSettingCommand command : commands) {
            JpaMarket market = enabledMarketMap.get(command.code());

            if (market == null) {
                continue;
            }

            if (existingSettingMap.containsKey(command.code())) {
                continue;
            }

            JpaPriceAlertSetting entity = JpaPriceAlertSetting.create(userPublicId, market, command.enabled(), command.targetChangeRate());
            jpaSettings.add(entity);
        }

        if (jpaSettings.isEmpty()) {
            return;
        }

        priceAlertSettingRepository.saveAll(jpaSettings);
    }

    private void updateSettings(
            List<UpdatePriceAlertSettingCommand> commands,
            Map<String, JpaMarket> enabledMarketMap,
            Map<String, JpaPriceAlertSetting> existingSettingMap
    ) {
        if (commands.isEmpty()) {
            return;
        }

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

    private void deleteSettings(List<DeletePriceAlertSettingCommand> commands, Map<String, JpaPriceAlertSetting> existingSettingMap) {
        if (commands.isEmpty()) {
            return;
        }

        List<Long> ids = commands.stream()
                .map(DeletePriceAlertSettingCommand::code)
                .map(existingSettingMap::get)
                .filter(Objects::nonNull)
                .map(JpaPriceAlertSetting::getId)
                .toList();

        if (ids.isEmpty()) {
            return;
        }

        priceAlertSettingRepository.deleteAllByIdInBatch(ids);
    }

    private Set<String> collectEnabledTargetCodes(ChangePriceAlertSettingsCommand command) {
        Set<String> codes = new HashSet<>();

        command.creates()
                .stream()
                .map(CreatePriceAlertSettingCommand::code)
                .forEach(codes::add);

        command.updates()
                .stream()
                .map(UpdatePriceAlertSettingCommand::code)
                .forEach(codes::add);

        return codes;
    }

    private Set<String> collectSettingTargetCodes(ChangePriceAlertSettingsCommand command) {
        Set<String> codes = new HashSet<>();

        command.creates()
                .stream()
                .map(CreatePriceAlertSettingCommand::code)
                .forEach(codes::add);

        command.updates()
                .stream()
                .map(UpdatePriceAlertSettingCommand::code)
                .forEach(codes::add);

        command.deletes()
                .stream()
                .map(DeletePriceAlertSettingCommand::code)
                .forEach(codes::add);

        return codes;
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

        return priceAlertSettingRepository.findAllByUserPublicIdAndMarketCodeIn(userPublicId, marketCodes)
                .stream()
                .collect(Collectors.toMap(
                        setting -> setting.getMarket().getMarketCode(),
                        Function.identity()
                ));
    }
}