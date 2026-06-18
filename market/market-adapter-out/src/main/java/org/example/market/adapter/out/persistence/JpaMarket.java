package org.example.market.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.common.jpa.BaseEntity;
import org.example.market.domain.model.Market;

@Entity
@Table(
        name = "market",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_markets_market_code",
                        columnNames = "market_code"
                )
        }
)
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JpaMarket extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "market_code", nullable = false, length = 30)
    private String marketCode;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "korean_name", nullable = false, length = 50)
    private String koreanName;

    @Column(name = "english_name", nullable = false, length = 80)
    private String englishName;

    @Column(nullable = false)
    private boolean enabled;

    public static JpaMarket create(
            String marketCode,
            String symbol,
            String koreanName,
            String englishName,
            boolean enabled
    ) {
        return JpaMarket.builder()
                .marketCode(marketCode)
                .symbol(symbol)
                .koreanName(koreanName)
                .englishName(englishName)
                .enabled(enabled)
                .build();
    }

    public Market toDomain() {
        return Market.rehydrate(
                id,
                marketCode,
                symbol,
                koreanName,
                englishName,
                enabled,
                createdAt,
                updatedAt
        );
    }

    public void update(
            String marketCode,
            String symbol,
            String koreanName,
            String englishName,
            boolean enabled
    ) {
        this.marketCode = marketCode;
        this.symbol = symbol;
        this.koreanName = koreanName;
        this.englishName = englishName;
        this.enabled = enabled;
    }
}