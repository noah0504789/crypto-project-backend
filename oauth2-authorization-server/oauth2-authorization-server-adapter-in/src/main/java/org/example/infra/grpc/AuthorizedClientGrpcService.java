package org.example.infra.grpc;

import com.google.protobuf.BoolValue;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.grpc.token.AuthorizedClientServiceGrpc;
import org.example.grpc.token.RemoveAuthorizedClientRequest;
import org.example.grpc.token.SaveAuthorizedClientRequest;
import org.example.oauth2.token.port.AuthorizedClientStore;

import java.util.Map;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class AuthorizedClientGrpcService extends AuthorizedClientServiceGrpc.AuthorizedClientServiceImplBase {

    private final AuthorizedClientStore authorizedClientStore;

    @Override
    public void save(SaveAuthorizedClientRequest request, StreamObserver<BoolValue> responseObserver) {
        String clientRegistrationId = request.getClientRegistrationId();
        String email = request.getEmail();
        String accessToken = request.getAccessToken();
        String refreshToken = request.getRefreshToken();
        Map<String, String> claims = request.getClaimsMap();

        boolean result = authorizedClientStore.save(clientRegistrationId, email, accessToken, refreshToken, claims);

        responseObserver.onNext(BoolValue.of(result));
        responseObserver.onCompleted();
    }

    @Override
    public void remove(RemoveAuthorizedClientRequest request, StreamObserver<BoolValue> responseObserver) {
        String email = request.getEmail();
        boolean result = authorizedClientStore.remove(email);

        responseObserver.onNext(BoolValue.of(result));
        responseObserver.onCompleted();
    }
}
