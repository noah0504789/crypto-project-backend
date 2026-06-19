package org.example.market.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.*;
import org.example.common.id.annotation.SnowflakeId;
import org.example.common.jpa.BaseEntity;
import org.example.market.domain.model.PriceAlertSetting;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "price_alert_setting",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_price_alert_setting_user_public_id_market_id",
                        columnNames = {"user_public_id", "market_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_price_alert_setting_market_id",
                        columnList = "market_id"
                )
        }
)
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JpaPriceAlertSetting extends BaseEntity {

    @Id
    @SnowflakeId
    private Long id;

    @Column(name = "user_public_id", nullable = false)
    private UUID userPublicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "market_id", nullable = false)
    private JpaMarket market;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "target_change_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal targetChangeRate;

    public static JpaPriceAlertSetting create(
            UUID userPublicId,
            JpaMarket market,
            boolean enabled,
            BigDecimal targetChangeRate
    ) {
        return JpaPriceAlertSetting.builder()
                .userPublicId(userPublicId)
                .market(market)
                .enabled(enabled)
                .targetChangeRate(targetChangeRate)
                .build();
    }

    public PriceAlertSetting toDomain() {
        return PriceAlertSetting.rehydrate(
                id,
                userPublicId,
                market.getId(),
                enabled,
                targetChangeRate,
                createdAt,
                updatedAt
        );
    }

    public void update(boolean enabled, BigDecimal targetChangeRate) {
        this.enabled = enabled;
        this.targetChangeRate = targetChangeRate;
    }
}