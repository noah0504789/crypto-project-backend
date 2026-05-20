package org.example.infra.grpc;

import com.google.protobuf.StringValue;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.grpc.token.FindRefreshTokenGrpcRequest;
import org.example.grpc.token.RefreshTokenServiceGrpc;
import org.example.oauth2.token.port.RefreshTokenStore;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class RefreshTokenGrpcService extends RefreshTokenServiceGrpc.RefreshTokenServiceImplBase {

    private final RefreshTokenStore refreshTokenStore;

    @Override
    public void findValue(FindRefreshTokenGrpcRequest request, StreamObserver<StringValue> responseObserver) {
        String refreshToken = refreshTokenStore.findValue(request.getClientRegistrationId(), request.getUsername());

        responseObserver.onNext(StringValue.of(refreshToken));
        responseObserver.onCompleted();
    }
}
