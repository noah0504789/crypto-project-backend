package org.example.market.adapter.in.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.market.adapter.in.web.dto.PriceAlertSettingChangeRequest;
import org.example.market.adapter.in.web.dto.MyPriceAlertSettingsResponse;
import org.example.market.application.port.in.PriceAlertSettingCommandUseCase;
import org.example.market.application.port.in.PriceAlertSettingQueryUseCase;
import org.example.market.application.service.result.MyPriceAlertSettingResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static org.example.common.enums.HttpHeaderKey.USER_ID_VALUE;

@RestController
@RequiredArgsConstructor
public class PriceAlertSettingController {

    private final PriceAlertSettingQueryUseCase priceAlertSettingQueryUseCase;
    private final PriceAlertSettingCommandUseCase priceAlertSettingCommandUseCase;

    @GetMapping("${api-path.market.price-alerts-me}")
    public ResponseEntity<MyPriceAlertSettingsResponse> getMySettings(
            @RequestHeader(USER_ID_VALUE) UUID userPublicId
    ) {
        List<MyPriceAlertSettingResult> results = priceAlertSettingQueryUseCase.getMySettings(userPublicId);

        return ResponseEntity.ok(MyPriceAlertSettingsResponse.from(results));
    }

    @PutMapping("${api-path.market.price-alerts-me}")
    public ResponseEntity<Void> changeMySettings(
            @RequestHeader(USER_ID_VALUE) UUID userPublicId,
            @RequestBody @Valid PriceAlertSettingChangeRequest request
    ) {
        priceAlertSettingCommandUseCase.changeMySettings(userPublicId, request.toCommand());

        return ResponseEntity.noContent().build();
    }
}
