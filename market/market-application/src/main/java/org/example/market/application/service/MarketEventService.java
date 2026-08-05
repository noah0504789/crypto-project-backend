package org.example.market.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.market.application.cache.MarketCacheNames;
import org.example.market.application.event.MarketCatalogChangedBroadcastEvent;
import org.example.market.application.port.in.MarketEventHandler;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketEventService implements MarketEventHandler {

    @Override
    @CacheEvict(cacheNames = MarketCacheNames.MARKETS, key = "'enabled'")
    public void handle(MarketCatalogChangedBroadcastEvent event, String txId) {
        log.info(
                "[market-cache] enabled markets cache evicted. event={}, txId={}",
                event.getClass().getSimpleName(),
                txId
        );
    }
}
