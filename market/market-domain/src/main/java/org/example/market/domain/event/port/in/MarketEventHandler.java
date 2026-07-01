package org.example.market.domain.event.port.in;

import org.example.market.domain.event.MarketCatalogChangedEvent;

public interface MarketEventHandler {

    void handle(MarketCatalogChangedEvent event, String txId);
}
