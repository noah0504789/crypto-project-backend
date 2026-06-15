package org.example.market.application.service.command;

public record UpdateMarketCommand(
        Long id,
        String marketCode,
        String symbol,
        String koreanName,
        String englishName,
        boolean enabled
) {
}