package org.example.market.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.example.common.jpa.BaseEntity;

@Entity
@Table(
    name = "market",
    uniqueConstraints = {@UniqueConstraint(name = "uk_markets_market_code", columnNames = "market_code")}
)
@Getter
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Market extends BaseEntity {

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
}