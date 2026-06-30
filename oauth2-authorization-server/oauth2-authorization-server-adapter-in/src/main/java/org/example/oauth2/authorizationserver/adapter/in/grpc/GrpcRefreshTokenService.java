package org.example.oauth2.authorizationserver.adapter.in.grpc;

import com.google.protobuf.StringValue;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.grpc.auth.GrpcFindRefreshTokenRequest;
import org.example.grpc.auth.RefreshTokenServiceGrpc;
import org.example.oauth2.authorizationserver.token.application.port.out.RefreshTokenPort;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class GrpcRefreshTokenService extends RefreshTokenServiceGrpc.RefreshTokenServiceImplBase {

    private final RefreshTokenPort refreshTokenPort;

    @Override
    public void findValue(GrpcFindRefreshTokenRequest request, StreamObserver<StringValue> responseObserver) {
        String refreshToken = refreshTokenPort.findValue(request.getClientRegistrationId(), request.getUsername());

        responseObserver.onNext(StringValue.of(refreshToken));
        responseObserver.onCompleted();
    }
}
