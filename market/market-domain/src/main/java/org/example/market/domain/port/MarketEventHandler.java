package org.example.market.domain.port;

import org.example.market.domain.event.MarketChangedEvent;

public interface MarketEventHandler {

    void handle(MarketChangedEvent event, String txId);
}
