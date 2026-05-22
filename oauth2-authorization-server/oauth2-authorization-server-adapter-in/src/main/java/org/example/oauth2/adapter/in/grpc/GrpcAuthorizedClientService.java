package org.example.oauth2.adapter.in.grpc;

import com.google.protobuf.BoolValue;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.grpc.token.AuthorizedClientServiceGrpc;
import org.example.grpc.token.RemoveAuthorizedClientRequest;
import org.example.grpc.token.SaveAuthorizedClientRequest;
import org.example.oauth2.token.port.out.AuthorizedClientPort;

import java.util.Map;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class GrpcAuthorizedClientService extends AuthorizedClientServiceGrpc.AuthorizedClientServiceImplBase {

    private final AuthorizedClientPort authorizedClientPort;

    @Override
    public void save(SaveAuthorizedClientRequest request, StreamObserver<BoolValue> responseObserver) {
        String clientRegistrationId = request.getClientRegistrationId();
        String email = request.getEmail();
        String accessToken = request.getAccessToken();
        String refreshToken = request.getRefreshToken();
        Map<String, String> claims = request.getClaimsMap();

        boolean result = authorizedClientPort.save(clientRegistrationId, email, accessToken, refreshToken, claims);

        responseObserver.onNext(BoolValue.of(result));
        responseObserver.onCompleted();
    }

    @Override
    public void remove(RemoveAuthorizedClientRequest request, StreamObserver<BoolValue> responseObserver) {
        String email = request.getEmail();
        boolean result = authorizedClientPort.remove(email);

        responseObserver.onNext(BoolValue.of(result));
        responseObserver.onCompleted();
    }
}
