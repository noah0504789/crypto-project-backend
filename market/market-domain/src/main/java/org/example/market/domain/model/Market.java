package org.example.market.domain.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.market.domain.event.MarketCatalogChangedEvent;
import org.example.market.domain.event.MarketEventList;

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

    private MarketEventList eventList;

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
                .eventList(new MarketEventList())
                .build();
    }

    public static Market eventSource() {
        return Market.builder()
                .eventList(new MarketEventList())
                .build();
    }

    public void catalogChanged() {
        eventList().addEvent(MarketCatalogChangedEvent.of());
    }

    public MarketEventList pullEventList() {
        MarketEventList pulledEventList = eventList();
        this.eventList = new MarketEventList();

        return pulledEventList;
    }

    private MarketEventList eventList() {
        if (this.eventList == null) {
            this.eventList = new MarketEventList();
        }

        return this.eventList;
    }
}