package org.example.market.application.port.out;

import org.example.market.application.service.command.ChangePriceAlertSettingsCommand;
import org.example.market.domain.model.PriceAlertSetting;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PriceAlertSettingPersistencePort {

    List<PriceAlertSetting> findAllByUserPublicId(UUID userPublicId);

    void changeSettings(UUID userPublicId, ChangePriceAlertSettingsCommand command);
}