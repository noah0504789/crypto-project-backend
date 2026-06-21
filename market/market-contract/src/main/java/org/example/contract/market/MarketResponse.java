package org.example.contract.market;

import lombok.Builder;

@Builder
public record MarketResponse(
        Long id,
        String marketCode,
        String symbol,
        String koreanName,
        String englishName
) {
}