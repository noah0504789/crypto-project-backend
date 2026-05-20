package org.example.infra.grpc;

import io.grpc.Channel;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.grpc.token.BlacklistTokenServiceGrpc;
import org.example.grpc.token.ExistsBlacklistTokenGrpcRequest;
import org.example.oauth2.port.BlacklistTokenPort;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class Oauth2AuthorizationServerGrpcClient implements BlacklistTokenPort {

    @GrpcClient("oauth2-authorization-server-client")
    private Channel channel;

//    private final ManagedChannel oauth2AuthorizationServerChannel;

    public boolean existsByAccessToken(String accessToken) {
        ExistsBlacklistTokenGrpcRequest request = ExistsBlacklistTokenGrpcRequest.newBuilder()
                .setAccessToken(accessToken)
                .build();

//        BlacklistTokenServiceGrpc.BlacklistTokenServiceBlockingStub stub = BlacklistTokenServiceGrpc.newBlockingStub(oauth2AuthorizationServerChannel).withDeadlineAfter(3500, TimeUnit.MILLISECONDS);
        BlacklistTokenServiceGrpc.BlacklistTokenServiceBlockingStub stub = BlacklistTokenServiceGrpc.newBlockingStub(channel).withDeadlineAfter(3500, TimeUnit.MILLISECONDS);

        return stub.exists(request).getValue();
    }
}
