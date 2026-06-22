package org.example.market.application.service;

import lombok.RequiredArgsConstructor;
import org.example.market.application.cache.MarketCacheNames;
import org.example.market.application.port.in.MarketQueryUseCase;
import org.example.market.application.port.out.MarketPersistencePort;
import org.example.market.domain.model.Market;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketQueryService implements MarketQueryUseCase {

    private final MarketPersistencePort marketPersistencePort;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = MarketCacheNames.MARKETS, key = "'enabled'")
    public List<Market> getMarkets() {
        return marketPersistencePort.findAllEnabledOrderByIdAsc();
    }
}