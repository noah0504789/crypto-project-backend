package org.example.market.application.service.command;

public record CreateMarketCommand(
        String marketCode,
        String symbol,
        String koreanName,
        String englishName,
        boolean enabled
) {
}