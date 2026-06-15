package org.example.market.adapter.in.web.dto;

import org.example.market.domain.model.Market;

public record MarketResponse(
        Long id,
        String marketCode,
        String symbol,
        String koreanName,
        String englishName
) {

    public static MarketResponse from(Market market) {
        return new MarketResponse(
                market.getId(),
                market.getMarketCode(),
                market.getSymbol(),
                market.getKoreanName(),
                market.getEnglishName()
        );
    }
}