package org.example.market.application.port.out;

import org.example.market.application.service.command.ChangeMarketsCommand;
import org.example.market.domain.model.Market;

import java.util.List;
import java.util.Set;

public interface MarketPersistencePort {

    List<Market> findAllEnabledOrderByIdAsc();

    List<Market> findAllEnabledByIds(Set<Long> ids);

    void createMarkets(List<ChangeMarketsCommand.CreateMarketCommand> commands);

    void updateMarkets(List<ChangeMarketsCommand.UpdateMarketCommand> commands);

    void deleteMarketsByIds(List<Long> marketIds);
}