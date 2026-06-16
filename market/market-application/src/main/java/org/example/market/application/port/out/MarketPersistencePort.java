package org.example.market.application.port.out;

import org.example.market.application.service.command.ChangeMarketsCommand;
import org.example.market.domain.model.Market;

import java.util.List;

public interface MarketPersistencePort {

    List<Market> findAllByEnabledTrueOrderByIdAsc();

    void changeMarkets(ChangeMarketsCommand command);
}