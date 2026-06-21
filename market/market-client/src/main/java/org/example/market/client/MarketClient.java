package org.example.market.client;

import org.example.contract.market.MarketResponse;

import java.util.List;

public interface MarketClient {

    List<MarketResponse> getEnabledMarkets();
}
