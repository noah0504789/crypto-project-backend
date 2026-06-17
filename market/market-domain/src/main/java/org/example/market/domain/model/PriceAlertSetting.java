package org.example.market.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PriceAlertSetting {

    private Long id;
    private UUID userPublicId;
    private Long marketId;
    private boolean enabled;
    private BigDecimal targetChangeRate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PriceAlertSetting rehydrate(
            Long id,
            UUID userPublicId,
            Long marketId,
            boolean enabled,
            BigDecimal targetChangeRate,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return PriceAlertSetting.builder()
                .id(id)
                .userPublicId(userPublicId)
                .marketId(marketId)
                .enabled(enabled)
                .targetChangeRate(targetChangeRate)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}
