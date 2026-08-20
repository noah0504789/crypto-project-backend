package org.example.market.client;

import io.grpc.Channel;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.common.grpc.client.GrpcFutures;
import org.example.grpc.market.GrpcGetEnabledMarketsRequest;
import org.example.grpc.market.GrpcGetEnabledMarketsResponse;
import org.example.grpc.market.MarketServiceGrpc;
import org.example.market.client.properties.GrpcMarketClientProperties;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GrpcMarketClient implements MarketClient {

    @GrpcClient("market-client")
    private Channel channel;

    private final GrpcMarketClientProperties grpcMarketClientProperties;

    @Override
    public CompletableFuture<GrpcGetEnabledMarketsResponse> getEnabledMarkets() {
        GrpcGetEnabledMarketsRequest request = GrpcGetEnabledMarketsRequest.newBuilder()
                .build();

        return GrpcFutures.toCompletableFuture(stub().getEnabledMarkets(request));
    }

    private MarketServiceGrpc.MarketServiceFutureStub stub() {
        return MarketServiceGrpc.newFutureStub(channel)
                .withDeadlineAfter(grpcMarketClientProperties.deadlineMillis(), TimeUnit.MILLISECONDS);
    }
}
