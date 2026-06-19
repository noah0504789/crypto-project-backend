package org.example.market.application.port.in;

import org.example.market.application.service.command.ChangePriceAlertSettingsCommand;

import java.util.UUID;

public interface PriceAlertSettingCommandUseCase {

    void changeMySettings(UUID userPublicId, ChangePriceAlertSettingsCommand command);
}