package org.example.market.application.service;

import lombok.RequiredArgsConstructor;
import org.example.market.application.port.in.MarketCommandUseCase;
import org.example.market.application.port.out.MarketPersistencePort;
import org.example.market.application.service.command.ChangeMarketsCommand;
import org.example.market.domain.event.MarketCatalogChangedEvent;
import org.example.market.domain.event.MarketEventList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketCommandService implements MarketCommandUseCase {

    private final MarketPersistencePort marketPersistencePort;

    @Override
    @Transactional
    public void changeMarkets(ChangeMarketsCommand command) {
        if (command.isEmpty()) {
            return;
        }

        marketPersistencePort.changeMarkets(command);

        publishMarketChangedEvent();
    }

    private void publishMarketChangedEvent() {
        MarketEventList eventList = new MarketEventList();
        eventList.addEvent(MarketCatalogChangedEvent.of());
        eventList.publish();
    }
}