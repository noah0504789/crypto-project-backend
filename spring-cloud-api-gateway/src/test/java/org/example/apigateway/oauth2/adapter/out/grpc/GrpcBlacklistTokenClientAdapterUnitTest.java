package org.example.apigateway.oauth2.adapter.out.grpc;

import java.util.concurrent.CompletableFuture;
import org.example.oauth2.authorizationserver.client.Oauth2AuthorizationServerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class GrpcBlacklistTokenClientAdapterUnitTest {

    private static final String ACCESS_TOKEN = "access-token";

    @Mock
    private Oauth2AuthorizationServerClient authorizationServerClient;

    private GrpcBlacklistTokenClientAdapter sut;

    @BeforeEach
    void setUp() {
        sut = new GrpcBlacklistTokenClientAdapter(authorizationServerClient);
    }

    @Test
    @DisplayName("구독할 때 공용 client의 async blacklist 조회를 호출한다")
    void existsByAccessToken_shouldCallAsyncClientLazily() {
        // given
        CompletableFuture<Boolean> response = new CompletableFuture<>();
        given(authorizationServerClient.existsBlacklistAsync(ACCESS_TOKEN)).willReturn(response);

        // when
        Mono<Boolean> result = sut.existsByAccessToken(ACCESS_TOKEN);

        // then
        verifyNoInteractions(authorizationServerClient);

        StepVerifier.create(result)
                .then(() -> response.complete(true))
                .expectNext(true)
                .verifyComplete();

        then(authorizationServerClient)
                .should()
                .existsBlacklistAsync(ACCESS_TOKEN);
    }

    @Test
    @DisplayName("공용 client 오류를 reactive subscriber에게 전달한다")
    void existsByAccessToken_shouldPropagateClientError() {
        // given
        CompletableFuture<Boolean> response = new CompletableFuture<>();
        IllegalStateException error = new IllegalStateException("blacklist lookup failed");
        given(authorizationServerClient.existsBlacklistAsync(ACCESS_TOKEN)).willReturn(response);

        // when & then
        StepVerifier.create(sut.existsByAccessToken(ACCESS_TOKEN))
                .then(() -> response.completeExceptionally(error))
                .expectErrorMatches(actual -> actual == error)
                .verify();
    }

    @Test
    @DisplayName("구독을 취소하면 공용 client의 future를 취소한다")
    void existsByAccessToken_shouldCancelClientFuture_whenSubscriptionIsCancelled() {
        // given
        CompletableFuture<Boolean> response = new CompletableFuture<>();
        given(authorizationServerClient.existsBlacklistAsync(ACCESS_TOKEN)).willReturn(response);

        // when
        StepVerifier.create(sut.existsByAccessToken(ACCESS_TOKEN))
                .thenCancel()
                .verify();

        // then
        assertThat(response).isCancelled();
    }
}
