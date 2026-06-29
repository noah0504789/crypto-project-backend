package org.example.market.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.outbox.application.port.out.OutboxEventListPublishPort;
import org.example.common.outbox.exception.TemporaryOutboxPersistenceException;
import org.example.market.application.port.in.MarketCommandUseCase;
import org.example.market.application.port.out.MarketPersistencePort;
import org.example.market.application.service.command.ChangeMarketsCommand;
import org.example.market.common.exception.MarketPersistException;
import org.example.market.domain.model.Market;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketCommandService implements MarketCommandUseCase {

    private final MarketPersistencePort marketPersistencePort;
    private final OutboxEventListPublishPort outboxEventListPublishPort;

    @Override
    @Transactional
    public void changeMarkets(ChangeMarketsCommand command) {
        if (command.isEmpty()) {
            return;
        }

        marketPersistencePort.changeMarkets(command);

        Market market = Market.eventSource();

        publishMarketChangedEvent(market);
    }

    private void publishMarketChangedEvent(Market market) {
        market.catalogChanged();

        try {
            outboxEventListPublishPort.publish(market.pullEventList());
        } catch (TemporaryOutboxPersistenceException e) {
            throw e;
        } catch (Exception e) {
            throw new MarketPersistException("failed to publish market changed event", e);
        }
    }
}