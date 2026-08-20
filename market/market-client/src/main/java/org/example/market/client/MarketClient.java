package org.example.market.client;

import org.example.grpc.market.GrpcGetEnabledMarketsResponse;

import java.util.concurrent.CompletableFuture;

public interface MarketClient {

    CompletableFuture<GrpcGetEnabledMarketsResponse> getEnabledMarkets();
}
