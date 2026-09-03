package org.example.apigateway.endpoint;

import com.google.protobuf.BoolValue;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.example.apigateway.config.ReactiveSecurityConfig;
import org.example.apigateway.config.TestDownstreamServerConfig;
import org.example.apigateway.config.TestGatewayCorsConfig;
import org.example.apigateway.config.TestGatewayRouteConfig;
import org.example.apigateway.config.TestPropertiesConfig;
import org.example.apigateway.config.TestWebFluxObjectMapperConfig;
import org.example.apigateway.filter.IdentityPropagationGlobalFilter;
import org.example.apigateway.oauth2.adapter.out.grpc.GrpcBlacklistTokenClientAdapter;
import org.example.apigateway.oauth2.application.service.BlacklistTokenService;
import org.example.apigateway.oauth2.validator.BlacklistAwareReactiveJwtDecoder;
import org.example.apigateway.oauth2.validator.ReactiveBlacklistTokenValidator;
import org.example.common.test.config.TestBootApplication;
import org.example.grpc.auth.BlacklistTokenServiceGrpc;
import org.example.grpc.auth.GrpcExistsBlacklistTokenRequest;
import org.example.oauth2.authorizationserver.client.GrpcOauth2AuthorizationServerClient;
import org.example.oauth2.authorizationserver.client.properties.GrpcOauth2AuthorizationServerClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {
                TestBootApplication.class,
                ReactiveSecurityConfig.class,
                IdentityPropagationGlobalFilter.class,

                TestWebFluxObjectMapperConfig.class,
                TestGatewayCorsConfig.class,
                TestGatewayRouteConfig.class,
                TestPropertiesConfig.class,
                TestDownstreamServerConfig.class,
                GrpcBlacklistDeadlineE2ETest.DelayedBlacklistGrpcConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EnableAutoConfiguration
@AutoConfigureWebTestClient
class GrpcBlacklistDeadlineE2ETest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private TestDownstreamServerConfig.TestDownstreamServers downstreamServers;

    @BeforeEach
    void setUp() {
        downstreamServers.reset();
    }

    @Test
    @DisplayName("blacklist gRPC 응답이 deadline을 넘으면 보호 경로를 500으로 차단한다")
    void protectedRoute_shouldFailClosed_whenBlacklistGrpcDeadlineExceeded() {
        webTestClient.get()
                .uri("/user/me/profile")
                .headers(headers -> headers.setBearerAuth("user-token"))
                .exchange()
                .expectStatus().is5xxServerError();

        assertThat(downstreamServers.userRequestCount()).isZero();
    }

    @TestConfiguration
    static class DelayedBlacklistGrpcConfig {

        private static final Duration DEADLINE = Duration.ofMillis(200);

        @Bean(destroyMethod = "shutdownNow")
        Server delayedBlacklistServer() throws IOException {
            return ServerBuilder.forPort(0)
                    .addService(new BlacklistTokenServiceGrpc.BlacklistTokenServiceImplBase() {
                        @Override
                        public void exists(GrpcExistsBlacklistTokenRequest request, StreamObserver<BoolValue> responseObserver) {
                        }
                    })
                    .build()
                    .start();
        }

        @Bean(destroyMethod = "shutdownNow")
        ManagedChannel delayedBlacklistChannel(Server delayedBlacklistServer) {
            return ManagedChannelBuilder.forAddress("localhost", delayedBlacklistServer.getPort())
                    .usePlaintext()
                    .build();
        }

        @Bean
        @Primary
        ReactiveJwtDecoder deadlineReactiveJwtDecoder(ManagedChannel delayedBlacklistChannel) {
            GrpcOauth2AuthorizationServerClient client = new GrpcOauth2AuthorizationServerClient(
                    new GrpcOauth2AuthorizationServerClientProperties(DEADLINE)
            );
            ReflectionTestUtils.setField(client, "channel", delayedBlacklistChannel);

            GrpcBlacklistTokenClientAdapter adapter = new GrpcBlacklistTokenClientAdapter(client);
            BlacklistTokenService service = new BlacklistTokenService(adapter);
            ReactiveBlacklistTokenValidator validator = new ReactiveBlacklistTokenValidator(service);

            return new BlacklistAwareReactiveJwtDecoder(token -> Mono.just(jwt(token)), validator);
        }

        private Jwt jwt(String token) {
            Instant now = Instant.now();

            return Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(3600))
                    .subject("user-1")
                    .claim("id", "user-1")
                    .claim("roles", List.of("ROLE_USER"))
                    .build();
        }
    }
}
