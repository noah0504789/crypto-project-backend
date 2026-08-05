package org.example.market.application.port.in;

import org.example.market.application.event.MarketCatalogChangedBroadcastEvent;

public interface MarketEventHandler {

    void handle(MarketCatalogChangedBroadcastEvent event, String txId);
}
