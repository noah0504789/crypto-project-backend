package org.example.market.application.port.in;

import org.example.market.application.service.command.ChangeMarketsCommand;

public interface MarketCommandUseCase {

    void changeMarkets(ChangeMarketsCommand command);
}