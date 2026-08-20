package org.example.oauth2.authorizationserver.client;

import com.google.protobuf.BoolValue;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.example.grpc.auth.BlacklistTokenServiceGrpc;
import org.example.grpc.auth.GrpcExistsBlacklistTokenRequest;
import org.example.oauth2.authorizationserver.client.properties.GrpcOauth2AuthorizationServerClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcOauth2AuthorizationServerClientUnitTest {

    private static final String ACCESS_TOKEN = "access-token";

    @Mock
    private Channel channel;

    @Mock
    private ClientCall<GrpcExistsBlacklistTokenRequest, BoolValue> clientCall;

    private AtomicReference<ClientCall.Listener<BoolValue>> responseListener;
    private GrpcOauth2AuthorizationServerClient sut;

    @BeforeEach
    void setUp() {
        responseListener = new AtomicReference<>();
        GrpcOauth2AuthorizationServerClientProperties properties =
                new GrpcOauth2AuthorizationServerClientProperties(Duration.ofSeconds(3));
        sut = new GrpcOauth2AuthorizationServerClient(properties);
        ReflectionTestUtils.setField(sut, "channel", channel);

        when(channel.newCall(eq(BlacklistTokenServiceGrpc.getExistsMethod()), any(CallOptions.class)))
                .thenAnswer(ignored -> clientCall);
        doAnswer(invocation -> {
            responseListener.set(invocation.getArgument(0));
            return null;
        }).when(clientCall).start(any(), any(Metadata.class));
    }

    @Test
    @DisplayName("future stub에 deadline을 적용하고 blacklist 결과를 CompletableFuture로 반환한다")
    void existsBlacklistAsync_shouldReturnCompletableFutureWithDeadline() throws Exception {
        // given
        completeCall(BoolValue.of(true));

        // when
        CompletableFuture<Boolean> result = sut.existsBlacklistAsync(ACCESS_TOKEN);

        // then
        assertThat(result.get(1, TimeUnit.SECONDS)).isTrue();

        ArgumentCaptor<CallOptions> callOptionsCaptor = ArgumentCaptor.forClass(CallOptions.class);
        then(channel)
                .should()
                .newCall(eq(BlacklistTokenServiceGrpc.getExistsMethod()), callOptionsCaptor.capture());

        assertThat(callOptionsCaptor.getValue().getDeadline()).isNotNull();
        assertThat(callOptionsCaptor.getValue().getDeadline().isExpired()).isFalse();
    }

    @Test
    @DisplayName("gRPC 오류를 CompletableFuture에 전달한다")
    void existsBlacklistAsync_shouldPropagateGrpcError() {
        // given
        failCall(Status.UNAVAILABLE);

        // when
        CompletableFuture<Boolean> result = sut.existsBlacklistAsync(ACCESS_TOKEN);

        // then
        CompletionException error = assertThrows(CompletionException.class, result::join);
        assertThat(Status.fromThrowable(error.getCause()).getCode()).isEqualTo(Status.Code.UNAVAILABLE);
    }

    @Test
    @DisplayName("CompletableFuture를 취소하면 진행 중인 gRPC call을 취소한다")
    void existsBlacklistAsync_shouldCancelGrpcCall_whenFutureIsCancelled() {
        // when
        CompletableFuture<Boolean> result = sut.existsBlacklistAsync(ACCESS_TOKEN);
        result.cancel(true);

        // then
        then(clientCall)
                .should()
                .cancel(any(), any());
    }

    private void completeCall(BoolValue response) {
        doAnswer(invocation -> {
            responseListener.get().onMessage(response);
            responseListener.get().onClose(Status.OK, new Metadata());
            return null;
        }).when(clientCall).halfClose();
    }

    private void failCall(Status status) {
        doAnswer(invocation -> {
            responseListener.get().onClose(status, new Metadata());
            return null;
        }).when(clientCall).halfClose();
    }
}
