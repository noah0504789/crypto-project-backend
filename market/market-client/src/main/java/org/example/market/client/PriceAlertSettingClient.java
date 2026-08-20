package org.example.market.client;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import org.example.grpc.market.GrpcFindPriceAlertReceiversResponse;

public interface PriceAlertSettingClient {

    CompletableFuture<GrpcFindPriceAlertReceiversResponse> findReceiverIds(
            String marketCode, BigDecimal targetChangeRate);
}
