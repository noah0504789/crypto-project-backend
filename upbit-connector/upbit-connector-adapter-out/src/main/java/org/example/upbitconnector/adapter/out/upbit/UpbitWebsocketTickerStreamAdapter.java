package org.example.upbitconnector.adapter.out.upbit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.grpc.market.GrpcGetEnabledMarketsResponse;
import org.example.grpc.market.GrpcMarket;
import org.example.market.client.MarketClient;
import org.example.upbitconnector.adapter.out.upbit.dto.UpbitWebsocketRequest;
import org.example.upbitconnector.application.port.out.UpbitTickerStreamPort;
import org.example.upbitconnector.application.properties.UpbitProperties;
import org.example.upbitconnector.contract.event.UpbitTickerEvent;
import org.example.upbitconnector.infra.exception.UpbitWebsocketException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.websocket.WebsocketInbound;
import reactor.netty.http.websocket.WebsocketOutbound;
import reactor.util.retry.Retry;

@Slf4j
@Component
@RequiredArgsConstructor
public class UpbitWebsocketTickerStreamAdapter implements UpbitTickerStreamPort {

    private static final String TICKER_TYPE = "ticker";

    private final ObjectMapper objectMapper;
    private final UpbitProperties properties;
    private final HttpClient upbitHttpClient;
    private final MarketClient marketClient;

    @Override
    public Flux<UpbitTickerEvent> ticker() {
        UpbitProperties.Websocket websocket = properties.websocket();

        return upbitHttpClient.websocket()
                .uri(websocket.url())
                .handle(this::receiveTicker)
                .doOnSubscribe(subscription -> logConnecting(websocket))
                .mapNotNull(this::toTickerEvent)
                // 서버가 정상 종료해도 수집은 계속돼야 한다. 즉시 재구독하면 폭주하므로 최소 백오프를 둔다.
                .repeatWhen(completed -> completed.delayElements(websocket.reconnectMinBackoff()))
                .retryWhen(reconnectBackoff(websocket));
    }

    private Flux<byte[]> receiveTicker(WebsocketInbound inbound, WebsocketOutbound outbound) {
        return outbound.sendString(subscribePayload())
                .then()
                .thenMany(inbound.receive().asByteArray());
    }

    private Retry reconnectBackoff(UpbitProperties.Websocket websocket) {
        return Retry.backoff(Long.MAX_VALUE, websocket.reconnectMinBackoff())
                .maxBackoff(websocket.reconnectMaxBackoff())
                .jitter(0.5)
                .doBeforeRetry(this::logRetry);
    }

    private void logConnecting(UpbitProperties.Websocket websocket) {
        log.info("[upbit] websocket connecting. url={}", websocket.url());
    }

    private void logRetry(Retry.RetrySignal signal) {
        log.warn("[upbit] websocket retry. attempts={}", signal.totalRetries() + 1, signal.failure());
    }

    private Mono<String> subscribePayload() {
        return Mono.defer(() -> Mono.fromFuture(marketClient.getEnabledMarkets()))
                .map(this::subscribeCodes)
                .map(this::serializeSubscribeRequest);
    }

    private List<String> subscribeCodes(GrpcGetEnabledMarketsResponse response) {
        List<String> codes = response.getMarketsList().stream()
                .map(GrpcMarket::getMarketCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();

        if (codes.isEmpty()) {
            throw new IllegalStateException("No enabled markets found for Upbit ticker subscription.");
        }

        log.info("[upbit] resolved upbit subscribe codes. codes={}", codes);

        return codes;
    }

    private String serializeSubscribeRequest(List<String> codes) {
        UpbitWebsocketRequest request = UpbitWebsocketRequest.ticker(properties.websocket().ticket(), codes);

        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new UpbitWebsocketException("Failed to serialize upbit websocket request", e);
        }
    }

    private UpbitTickerEvent toTickerEvent(byte[] frame) {
        try {
            UpbitTickerEvent event = objectMapper.readValue(frame, UpbitTickerEvent.class);

            return TICKER_TYPE.equals(event.type()) ? event : null;
        } catch (Exception e) {
            throw new UpbitWebsocketException("Failed to deserialize upbit websocket message", e);
        }
    }
}
