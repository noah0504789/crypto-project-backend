package org.example.market.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.market.application.service.command.ChangePriceAlertSettingsCommand;

import java.math.BigDecimal;
import java.util.List;

public record PriceAlertSettingChangeRequest(
        List<@Valid CreatePriceAlertSettingRequest> creates,
        List<@Valid UpdatePriceAlertSettingRequest> updates,
        List<@Valid DeletePriceAlertSettingRequest> deletes
) {

    public ChangePriceAlertSettingsCommand toCommand() {
        return new ChangePriceAlertSettingsCommand(
                creates == null ? List.of() : creates.stream().map(CreatePriceAlertSettingRequest::toCommand).toList(),
                updates == null ? List.of() : updates.stream().map(UpdatePriceAlertSettingRequest::toCommand).toList(),
                deletes == null ? List.of() : deletes.stream().map(DeletePriceAlertSettingRequest::toCommand).toList()
        );
    }

    public record CreatePriceAlertSettingRequest(
            @NotBlank(message = "{priceAlertSetting.code.notBlank}")
            String code,

            boolean enabled,

            @NotNull(message = "{priceAlertSetting.targetChangeRate.notNull}")
            @DecimalMin(value = "0.00", message = "{priceAlertSetting.targetChangeRate.min}")
            @DecimalMax(value = "1.00", message = "{priceAlertSetting.targetChangeRate.max}")
            BigDecimal targetChangeRate
    ) {

        public ChangePriceAlertSettingsCommand.CreatePriceAlertSettingCommand toCommand() {
            return new ChangePriceAlertSettingsCommand.CreatePriceAlertSettingCommand(code, enabled, targetChangeRate);
        }
    }

    public record UpdatePriceAlertSettingRequest(
            @NotBlank(message = "{priceAlertSetting.code.notBlank}")
            String code,

            boolean enabled,

            @NotNull(message = "{priceAlertSetting.targetChangeRate.notNull}")
            @DecimalMin(value = "0.00", message = "{priceAlertSetting.targetChangeRate.min}")
            @DecimalMax(value = "1.00", message = "{priceAlertSetting.targetChangeRate.max}")
            BigDecimal targetChangeRate
    ) {

        public ChangePriceAlertSettingsCommand.UpdatePriceAlertSettingCommand toCommand() {
            return new ChangePriceAlertSettingsCommand.UpdatePriceAlertSettingCommand(code, enabled, targetChangeRate);
        }
    }

    public record DeletePriceAlertSettingRequest(
            @NotBlank(message = "{priceAlertSetting.code.notBlank}")
            String code
    ) {

        public ChangePriceAlertSettingsCommand.DeletePriceAlertSettingCommand toCommand() {
            return new ChangePriceAlertSettingsCommand.DeletePriceAlertSettingCommand(code);
        }
    }
}
