package org.example.market.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.example.market.adapter.in.web.dto.MarketResponse;
import org.example.market.application.port.in.MarketQueryUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MarketController {

    private final MarketQueryUseCase marketQueryUseCase;

    @GetMapping("${api-path.market.markets:/markets}")
    public ResponseEntity<List<MarketResponse>> getMarkets() {
        List<MarketResponse> response = marketQueryUseCase.getMarkets()
                .stream()
                .map(MarketResponse::from)
                .toList();

        return ResponseEntity.ok(response);
    }
}
