package org.example.market.application.port.out;

import org.example.market.application.service.command.ChangePriceAlertSettingsCommand;
import org.example.market.domain.model.PriceAlertSetting;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PriceAlertSettingPersistencePort {

    List<PriceAlertSetting> findAllByUserPublicId(UUID userPublicId);

    List<UUID> findReceiverIds(String marketCode, BigDecimal targetChangeRate);

    void deleteSettingsByCodes(UUID userPublicId, List<String> codes);

    void updateSettings(UUID userPublicId, List<ChangePriceAlertSettingsCommand.UpdatePriceAlertSettingCommand> commands);

    void createSettings(UUID userPublicId, List<ChangePriceAlertSettingsCommand.CreatePriceAlertSettingCommand> commands);
}