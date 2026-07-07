package org.example.market.application.port.in;

import org.example.market.application.event.MarketCatalogChangedEvent;

public interface MarketEventHandler {

    void handle(MarketCatalogChangedEvent event, String txId);
}
