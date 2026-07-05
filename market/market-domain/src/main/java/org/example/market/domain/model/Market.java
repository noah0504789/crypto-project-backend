package org.example.market.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Market {

    private Long id;
    private String marketCode;
    private String symbol;
    private String koreanName;
    private String englishName;
    private boolean enabled;
    protected LocalDateTime createdAt;
    protected LocalDateTime updatedAt;

    public static Market rehydrate(
            Long id,
            String marketCode,
            String symbol,
            String koreanName,
            String englishName,
            boolean enabled,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return Market.builder()
                .id(id)
                .marketCode(marketCode)
                .symbol(symbol)
                .koreanName(koreanName)
                .englishName(englishName)
                .enabled(enabled)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}