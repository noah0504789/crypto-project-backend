package org.example.market.adapter.in.grpc;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.grpc.market.GrpcGetEnabledMarketsRequest;
import org.example.grpc.market.GrpcGetEnabledMarketsResponse;
import org.example.grpc.market.GrpcMarket;
import org.example.grpc.market.MarketServiceGrpc;
import org.example.market.application.port.in.MarketQueryUseCase;
import org.example.market.domain.model.Market;

@GrpcService
@RequiredArgsConstructor
public class GrpcMarketService extends MarketServiceGrpc.MarketServiceImplBase {

    private final MarketQueryUseCase marketQueryUseCase;

    @Override
    public void getEnabledMarkets(GrpcGetEnabledMarketsRequest request, StreamObserver<GrpcGetEnabledMarketsResponse> responseObserver) {
        GrpcGetEnabledMarketsResponse response = GrpcGetEnabledMarketsResponse.newBuilder()
                .addAllMarkets(
                        marketQueryUseCase.getMarkets()
                                .stream()
                                .map(this::toGrpc)
                                .toList()
                )
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private GrpcMarket toGrpc(Market market) {
        return GrpcMarket.newBuilder()
                .setId(market.getId())
                .setMarketCode(market.getMarketCode())
                .setSymbol(market.getSymbol())
                .setKoreanName(market.getKoreanName())
                .setEnglishName(market.getEnglishName())
                .build();
    }
}