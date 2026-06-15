package org.example.market.application.port.in;

import org.example.market.domain.model.Market;

import java.util.List;

public interface MarketQueryUseCase {

    List<Market> getMarkets();
}