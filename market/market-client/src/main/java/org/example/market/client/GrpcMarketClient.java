package org.example.market.client;

import io.grpc.Channel;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.contract.market.MarketResponse;
import org.example.grpc.market.GrpcGetEnabledMarketsRequest;
import org.example.grpc.market.GrpcMarket;
import org.example.grpc.market.MarketServiceGrpc;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class GrpcMarketClient implements MarketClient {

    @GrpcClient("market-client")
    private Channel channel;

    @Override
    public List<MarketResponse> getEnabledMarkets() {
        GrpcGetEnabledMarketsRequest request = GrpcGetEnabledMarketsRequest.newBuilder()
                .build();

        return stub().getEnabledMarkets(request)
                .getMarketsList()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private MarketServiceGrpc.MarketServiceBlockingStub stub() {
        return MarketServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(3500, TimeUnit.MILLISECONDS);
    }

    private MarketResponse toResponse(GrpcMarket market) {
        return MarketResponse.builder()
                .id(market.getId())
                .marketCode(market.getMarketCode())
                .symbol(market.getSymbol())
                .koreanName(market.getKoreanName())
                .englishName(market.getEnglishName())
                .build();
    }
}