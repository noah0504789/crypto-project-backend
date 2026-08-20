package org.example.market.client;

import io.grpc.Channel;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.common.grpc.client.GrpcFutures;
import org.example.grpc.market.*;
import org.example.market.client.properties.GrpcMarketClientProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GrpcPriceAlertSettingClient implements PriceAlertSettingClient {

    @GrpcClient("market-client")
    private Channel channel;

    private final GrpcMarketClientProperties grpcMarketClientProperties;

    @Override
    public CompletableFuture<GrpcFindPriceAlertReceiversResponse> findReceiverIds(
            String marketCode, BigDecimal targetChangeRate) {
        if (marketCode == null || marketCode.isBlank() || targetChangeRate == null) {
            return CompletableFuture.completedFuture(
                    GrpcFindPriceAlertReceiversResponse.getDefaultInstance());
        }

        GrpcFindPriceAlertReceiversRequest request =
                GrpcFindPriceAlertReceiversRequest.newBuilder()
                        .setMarketCode(marketCode)
                        .setTargetChangeRate(targetChangeRate.toPlainString())
                        .build();

        return GrpcFutures.toCompletableFuture(stub().findReceiverIds(request));
    }

    private PriceAlertSettingServiceGrpc.PriceAlertSettingServiceFutureStub stub() {
        return PriceAlertSettingServiceGrpc.newFutureStub(channel)
                .withDeadlineAfter(grpcMarketClientProperties.deadlineMillis(), TimeUnit.MILLISECONDS);
    }
}
