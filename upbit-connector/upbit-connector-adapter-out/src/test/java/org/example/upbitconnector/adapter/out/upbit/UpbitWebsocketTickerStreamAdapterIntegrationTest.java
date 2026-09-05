package org.example.upbitconnector.adapter.out.upbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.grpc.market.GrpcGetEnabledMarketsResponse;
import org.example.grpc.market.GrpcMarket;
import org.example.market.client.MarketClient;
import org.example.upbitconnector.application.properties.UpbitProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class UpbitWebsocketTickerStreamAdapterIntegrationTest {

    private static final String TICKER_JSON = """
            {"type":"ticker","code":"KRW-BTC","trade_price":100.0,"trade_timestamp":1000}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> subscribeRequests = new CopyOnWriteArrayList<>();

    @Mock private MarketClient marketClient;

    private DisposableServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.disposeNow();
        }
    }

    @Test
    @DisplayName("활성 마켓을 정리해 구독하고 ticker 메시지를 역직렬화한다")
    void subscribesEnabledMarketsAndDeserializesTicker() throws Exception {
        given(marketClient.getEnabledMarkets())
                .willReturn(markets("KRW-BTC", "", "KRW-ETH", "KRW-BTC"));
        startServer((attempt, inbound, outbound) -> Mono.when(
                        outbound.sendString(Mono.just(TICKER_JSON)).then(),
                        inbound.receive().asString().next().doOnNext(subscribeRequests::add).then())
                .then(Mono.never()));

        StepVerifier.create(adapter().ticker().take(1))
                .assertNext(event -> {
                    assertThat(event.code()).isEqualTo("KRW-BTC");
                    assertThat(event.tradePrice()).isEqualTo(100.0);
                    assertThat(event.tradeTimestamp()).isEqualTo(1_000L);
                })
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        JsonNode actualRequest = objectMapper.readTree(subscribeRequests.get(0));
        JsonNode expectedRequest = objectMapper.readTree("""
                [
                  {"ticket":"test-ticket"},
                  {
                    "type":"ticker",
                    "codes":["KRW-BTC","KRW-ETH"],
                    "is_only_snapshot":false,
                    "is_only_realtime":true
                  }
                ]
                """);

        assertThat(actualRequest).isEqualTo(expectedRequest);
    }

    @Test
    @DisplayName("WebSocket이 정상 종료되면 활성 마켓을 다시 조회해 재연결한다")
    void reconnectsAfterNormalCompletion() {
        given(marketClient.getEnabledMarkets()).willReturn(markets("KRW-BTC"));
        startServer((attempt, inbound, outbound) -> attempt == 1
                ? outbound.sendClose()
                : sendResponse(outbound, TICKER_JSON));

        StepVerifier.create(adapter().ticker().take(1))
                .expectNextMatches(event -> "KRW-BTC".equals(event.code()))
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        verify(marketClient, atLeast(2)).getEnabledMarkets();
    }

    @Test
    @DisplayName("메시지 역직렬화에 실패하면 backoff 후 재연결한다")
    void reconnectsAfterDeserializationFailure() {
        given(marketClient.getEnabledMarkets()).willReturn(markets("KRW-BTC"));
        startServer((attempt, inbound, outbound) -> sendResponse(outbound, attempt == 1 ? "{" : TICKER_JSON));

        StepVerifier.create(adapter().ticker().take(1))
                .expectNextMatches(event -> "KRW-BTC".equals(event.code()))
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        verify(marketClient, atLeast(2)).getEnabledMarkets();
    }

    private void startServer(WebsocketScenario scenario) {
        AtomicInteger connectionAttempts = new AtomicInteger();

        server = HttpServer.create()
                .port(0)
                .route(routes -> routes.ws(
                        "/websocket/v1",
                        (inbound, outbound) -> scenario.handle(
                                connectionAttempts.incrementAndGet(), inbound, outbound)))
                .bindNow();
    }

    private UpbitWebsocketTickerStreamAdapter adapter() {
        return new UpbitWebsocketTickerStreamAdapter(
                objectMapper,
                properties(),
                HttpClient.create(),
                marketClient,
                new SimpleMeterRegistry());
    }

    private Mono<Void> sendResponse(
            reactor.netty.http.websocket.WebsocketOutbound outbound, String response) {
        return outbound.sendString(Mono.just(response)).neverComplete();
    }

    private UpbitProperties properties() {
        return new UpbitProperties(new UpbitProperties.Websocket(
                "ws://localhost:" + server.port() + "/websocket/v1",
                "test-ticket",
                Duration.ofSeconds(7),
                Duration.ofMillis(10),
                Duration.ofMillis(10)));
    }

    private CompletableFuture<GrpcGetEnabledMarketsResponse> markets(String... codes) {
        GrpcGetEnabledMarketsResponse response = GrpcGetEnabledMarketsResponse.newBuilder()
                .addAllMarkets(List.of(codes).stream()
                        .map(code -> GrpcMarket.newBuilder().setMarketCode(code).build())
                        .toList())
                .build();
        return CompletableFuture.completedFuture(response);
    }

    @FunctionalInterface
    private interface WebsocketScenario {

        Mono<Void> handle(
                int attempt,
                reactor.netty.http.websocket.WebsocketInbound inbound,
                reactor.netty.http.websocket.WebsocketOutbound outbound);
    }
}
