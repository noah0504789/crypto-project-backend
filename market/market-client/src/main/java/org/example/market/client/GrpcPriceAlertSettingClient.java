package org.example.market.client;

import io.grpc.Channel;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.grpc.market.*;
import org.example.market.client.properties.GrpcMarketClientProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GrpcPriceAlertSettingClient implements PriceAlertSettingClient {

    @GrpcClient("market-client")
    private Channel channel;

    private final GrpcMarketClientProperties grpcMarketClientProperties;

    @Override
    public List<UUID> findReceiverIds(String marketCode, BigDecimal targetChangeRate) {
        if (marketCode == null || marketCode.isBlank() || targetChangeRate == null) {
            return List.of();
        }

        GrpcFindPriceAlertReceiversRequest request =
                GrpcFindPriceAlertReceiversRequest.newBuilder()
                        .setMarketCode(marketCode)
                        .setTargetChangeRate(targetChangeRate.toPlainString())
                        .build();

        return stub().findReceiverIds(request)
                .getReceiverIdsList()
                .stream()
                .map(UUID::fromString)
                .toList();
    }

    private PriceAlertSettingServiceGrpc.PriceAlertSettingServiceBlockingStub stub() {
        return PriceAlertSettingServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(grpcMarketClientProperties.deadlineMillis(), TimeUnit.MILLISECONDS);
    }
}