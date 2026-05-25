package org.example.oauth2.authorizationserver.adapter.in.grpc;

import com.google.protobuf.StringValue;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.grpc.token.AccessTokenServiceGrpc;
import org.example.grpc.token.FindAccessTokenGrpcRequest;
import org.example.oauth2.authorizationserver.token.application.port.out.AccessTokenPort;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class GrpcAccessTokenService extends AccessTokenServiceGrpc.AccessTokenServiceImplBase {

    private final AccessTokenPort accessTokenPort;

    @Override
    public void findValue(FindAccessTokenGrpcRequest request, StreamObserver<StringValue> responseObserver) {
        String accessToken = accessTokenPort.findValue(request.getClientRegistrationId(), request.getUsername());

        responseObserver.onNext(StringValue.of(accessToken));
        responseObserver.onCompleted();
    }
}
