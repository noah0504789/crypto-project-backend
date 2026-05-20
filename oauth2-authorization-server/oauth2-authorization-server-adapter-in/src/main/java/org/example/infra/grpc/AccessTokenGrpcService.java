package org.example.infra.grpc;

import com.google.protobuf.StringValue;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.grpc.token.AccessTokenServiceGrpc;
import org.example.grpc.token.FindAccessTokenGrpcRequest;
import org.example.oauth2.token.port.AccessTokenStore;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class AccessTokenGrpcService extends AccessTokenServiceGrpc.AccessTokenServiceImplBase {

    private final AccessTokenStore accessTokenStore;

    @Override
    public void findValue(FindAccessTokenGrpcRequest request, StreamObserver<StringValue> responseObserver) {
        String accessToken = accessTokenStore.findValue(request.getClientRegistrationId(), request.getUsername());

        responseObserver.onNext(StringValue.of(accessToken));
        responseObserver.onCompleted();
    }
}
