package org.example.market.application.service;

import lombok.RequiredArgsConstructor;
import org.example.market.application.port.in.PriceAlertSettingCommandUseCase;
import org.example.market.application.port.out.PriceAlertSettingPersistencePort;
import org.example.market.application.service.command.ChangePriceAlertSettingsCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PriceAlertSettingCommandService implements PriceAlertSettingCommandUseCase {

    private final PriceAlertSettingPersistencePort priceAlertSettingPersistencePort;

    @Override
    @Transactional
    public void changeMySettings(UUID userPublicId, ChangePriceAlertSettingsCommand command) {
        if (command.isEmpty()) {
            return;
        }

        priceAlertSettingPersistencePort.changeSettings(userPublicId, command);
    }
}