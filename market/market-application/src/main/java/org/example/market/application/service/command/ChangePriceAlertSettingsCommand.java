package org.example.market.application.service.command;

import java.math.BigDecimal;
import java.util.List;

public record ChangePriceAlertSettingsCommand(
        List<CreatePriceAlertSettingCommand> creates,
        List<UpdatePriceAlertSettingCommand> updates,
        List<DeletePriceAlertSettingCommand> deletes
) {

    public ChangePriceAlertSettingsCommand {
        creates = creates == null ? List.of() : List.copyOf(creates);
        updates = updates == null ? List.of() : List.copyOf(updates);
        deletes = deletes == null ? List.of() : List.copyOf(deletes);
    }

    public boolean isEmpty() {
        return creates.isEmpty()
                && updates.isEmpty()
                && deletes.isEmpty();
    }

    public boolean hasCreates() {
        return !creates.isEmpty();
    }

    public boolean hasUpdates() {
        return !updates.isEmpty();
    }

    public boolean hasDeletes() {
        return !deletes.isEmpty();
    }

    public List<String> deleteCodes() {
        return deletes.stream()
                .map(DeletePriceAlertSettingCommand::code)
                .toList();
    }

    public record CreatePriceAlertSettingCommand(
            String code,
            boolean enabled,
            BigDecimal targetChangeRate
    ) {
    }

    public record UpdatePriceAlertSettingCommand(
            String code,
            boolean enabled,
            BigDecimal targetChangeRate
    ) {
    }

    public record DeletePriceAlertSettingCommand(
            String code
    ) {
    }
}