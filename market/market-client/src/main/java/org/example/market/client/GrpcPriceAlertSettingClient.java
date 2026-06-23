package org.example.market.client;

import io.grpc.Channel;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.grpc.market.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class GrpcPriceAlertSettingClient implements PriceAlertSettingClient {

    @GrpcClient("market-client")
    private Channel channel;

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
                .withDeadlineAfter(3500, TimeUnit.MILLISECONDS);
    }
}