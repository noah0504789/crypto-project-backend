package org.example.infra.grpc;

import com.google.protobuf.BoolValue;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.grpc.token.BlacklistTokenServiceGrpc;
import org.example.grpc.token.ExistsBlacklistTokenGrpcRequest;
import org.example.grpc.token.RegisterBlacklistTokenGrpcRequest;
import org.example.oauth2.token.port.BlacklistTokenStore;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class BlacklistTokenGrpcService extends BlacklistTokenServiceGrpc.BlacklistTokenServiceImplBase {

    private final BlacklistTokenStore blacklistTokenStore;

    @Override
    public void register(RegisterBlacklistTokenGrpcRequest request, StreamObserver<BoolValue> responseObserver) {
        blacklistTokenStore.register(request.getAccessToken());

        responseObserver.onNext(BoolValue.of(true));
        responseObserver.onCompleted();
    }

    @Override
    public void exists(ExistsBlacklistTokenGrpcRequest request, StreamObserver<BoolValue> responseObserver) {
        boolean result = blacklistTokenStore.existsByAccessToken(request.getAccessToken());

        responseObserver.onNext(BoolValue.of(result));
        responseObserver.onCompleted();
    }
}
