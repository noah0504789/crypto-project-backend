package org.example.market.application.service;

import lombok.RequiredArgsConstructor;
import org.example.market.application.cache.MarketCacheNames;
import org.example.market.application.port.in.MarketCommandUseCase;
import org.example.market.domain.event.MarketChangedEvent;
import org.example.market.domain.port.MarketEventHandler;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketEventService implements MarketEventHandler {

    @Override
    @CacheEvict(cacheNames = MarketCacheNames.MARKETS, key = "'enabled'")
    public void handle(MarketChangedEvent event, String txId) {
        // no-op
    }
}
