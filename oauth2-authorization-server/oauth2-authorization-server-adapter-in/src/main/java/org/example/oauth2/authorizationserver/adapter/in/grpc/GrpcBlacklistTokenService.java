package org.example.oauth2.authorizationserver.adapter.in.grpc;

import com.google.protobuf.BoolValue;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.grpc.auth.BlacklistTokenServiceGrpc;
import org.example.grpc.auth.GrpcExistsBlacklistTokenRequest;
import org.example.grpc.auth.GrpcRegisterBlacklistTokenRequest;
import org.example.oauth2.authorizationserver.token.application.port.out.BlacklistTokenPort;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class GrpcBlacklistTokenService extends BlacklistTokenServiceGrpc.BlacklistTokenServiceImplBase {

    private final BlacklistTokenPort blacklistTokenPort;

    @Override
    public void register(GrpcRegisterBlacklistTokenRequest request, StreamObserver<BoolValue> responseObserver) {
        blacklistTokenPort.register(request.getAccessToken());

        responseObserver.onNext(BoolValue.of(true));
        responseObserver.onCompleted();
    }

    @Override
    public void exists(GrpcExistsBlacklistTokenRequest request, StreamObserver<BoolValue> responseObserver) {
        boolean result = blacklistTokenPort.existsByAccessToken(request.getAccessToken());

        responseObserver.onNext(BoolValue.of(result));
        responseObserver.onCompleted();
    }
}
