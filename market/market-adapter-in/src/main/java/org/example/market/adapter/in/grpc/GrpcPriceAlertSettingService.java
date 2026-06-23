package org.example.market.adapter.in.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.grpc.market.GrpcFindPriceAlertReceiversRequest;
import org.example.grpc.market.GrpcFindPriceAlertReceiversResponse;
import org.example.grpc.market.PriceAlertSettingServiceGrpc;
import org.example.market.application.port.in.PriceAlertSettingQueryUseCase;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class GrpcPriceAlertSettingService extends PriceAlertSettingServiceGrpc.PriceAlertSettingServiceImplBase {

    private final PriceAlertSettingQueryUseCase priceAlertSettingQueryUseCase;

    @Override
    public void findReceiverIds(GrpcFindPriceAlertReceiversRequest request, StreamObserver<GrpcFindPriceAlertReceiversResponse> responseObserver) {
        if (request.getMarketCode().isBlank()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("market_code must not be blank")
                            .asRuntimeException()
            );
            return;
        }

        BigDecimal targetChangeRate = parseTargetChangeRate(request.getTargetChangeRate(), responseObserver);

        if (targetChangeRate == null) {
            return;
        }

        List<UUID> receiverIds = priceAlertSettingQueryUseCase.findReceiverIds(request.getMarketCode(), targetChangeRate);

        GrpcFindPriceAlertReceiversResponse response =
                GrpcFindPriceAlertReceiversResponse.newBuilder()
                        .addAllReceiverIds(
                                receiverIds.stream()
                                        .map(UUID::toString)
                                        .toList()
                        )
                        .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private BigDecimal parseTargetChangeRate(String targetChangeRate, StreamObserver<GrpcFindPriceAlertReceiversResponse> responseObserver) {
        if (targetChangeRate == null || targetChangeRate.isBlank()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("target_change_rate must not be blank")
                            .asRuntimeException()
            );
            return null;
        }

        try {
            return new BigDecimal(targetChangeRate);
        } catch (NumberFormatException e) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("target_change_rate is invalid")
                            .asRuntimeException()
            );
            return null;
        }
    }
}