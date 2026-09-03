package org.example.apigateway.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.example.apigateway.ratelimit.RateLimitConfig;
import org.example.common.test.config.TestBootApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

@SpringBootTest(
        classes = {
                TestBootApplication.class,
                ReactiveRouteConfig.class,
                RateLimitConfig.class,
                TestPropertiesConfig.class,
                TestFailingRedisConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@EnableAutoConfiguration
class ReactiveRouteContractIntegrationTest {

    @Autowired
    private List<RouteLocator> routeLocators;

    @Test
    @DisplayName("운영 Route ID는 문서의 대상 Service URI 계약과 일치한다")
    void routes_shouldMatchDocumentedServiceUris() {
        Map<String, URI> actual = routes().entrySet().stream()
                .collect(LinkedHashMap::new, (uris, entry) -> uris.put(entry.getKey(), entry.getValue().getUri()), Map::putAll);

        assertThat(actual).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
                route("oauth2-authorization-route", "lb://oauth2-client"),
                route("oauth2-callback-route", "lb://oauth2-client"),
                route("token-refresh-route", "lb://oauth2-client"),
                route("logout-route", "lb://oauth2-client"),
                route("oauth2-client-route", "lb://oauth2-client"),
                route("user-sign-up-route", "lb://user-service"),
                route("user-command-route", "lb://user-service"),
                route("user-query-route", "lb://user-service"),
                route("user-route", "lb://user-service"),
                route("market-command-route", "lb://market-service"),
                route("market-query-route", "lb://market-service"),
                route("market-public-query-route", "lb://market-service"),
                route("market-route", "lb://market-service"),
                route("notification-command-route", "lb://notification-service"),
                route("notification-query-route", "lb://notification-service"),
                route("notification-route", "lb://notification-service"),
                route("ws-native-upgrade", "lb:ws://websocket-gateway"),
                route("ws-upgrade", "lb:ws://websocket-gateway"),
                route("ws-http", "lb://websocket-gateway"),
                route("sockjs-route", "lb://websocket-gateway"),
                route("chat-command-route", "lb://chat-service"),
                route("chat-query-route", "lb://chat-service"),
                route("chat-public-query-route", "lb://chat-service"),
                route("chat-route", "lb://chat-service")
        ));
    }

    @Test
    @DisplayName("운영 fallback Route는 서비스별 Path rewrite와 gateway 헤더 계약을 적용한다")
    void fallbackRoutes_shouldApplyDocumentedRewriteAndHeaders() {
        assertFilterContract("oauth2-client-route", HttpMethod.POST, "/auth/logout", "/auth/logout", true);
        assertFilterContract("user-route", HttpMethod.GET, "/user/me/profile", "/api/v1/user/me/profile", true);
        assertFilterContract("market-route", HttpMethod.GET, "/markets", "/api/v1/markets", true);
        assertFilterContract("notification-route", HttpMethod.GET, "/notifications/me", "/api/v1/notifications/me", true);
        assertFilterContract("chat-route", HttpMethod.GET, "/chat/rooms/me", "/api/v1/chat/rooms/me", true);
        assertFilterContract("ws-http", HttpMethod.GET, "/ws/info", "/ws/info", false);
        assertFilterContract("sockjs-route", HttpMethod.POST, "/msg/chat.send", "/msg/chat.send", false);
    }

    private void assertFilterContract(String routeId, HttpMethod method, String externalPath, String downstreamPath, boolean gatewayHeaders) {
        Route route = routes().get(routeId);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, externalPath).build());
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, route);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = filteredExchange -> {
            captured.set(filteredExchange);
            return Mono.empty();
        };
        List<GatewayFilter> filters = new ArrayList<>(route.getFilters());
        AnnotationAwareOrderComparator.sort(filters);

        for (int i = filters.size() - 1; i >= 0; i--) {
            GatewayFilter filter = filters.get(i);
            GatewayFilterChain next = chain;
            chain = filteredExchange -> filter.filter(filteredExchange, next);
        }

        chain.filter(exchange).block();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getRequest().getPath().value()).isEqualTo(downstreamPath);

        if (gatewayHeaders) {
            assertThat(captured.get().getRequest().getHeaders().getFirst("X-From")).isEqualTo("gateway");
            assertThat(exchange.getResponse().getHeaders().getFirst("X-Gateway")).isEqualTo("reactive");
        } else {
            assertThat(captured.get().getRequest().getHeaders().containsKey("X-From")).isFalse();
            assertThat(exchange.getResponse().getHeaders().containsKey("X-Gateway")).isFalse();
        }
    }

    private Map<String, Route> routes() {
        return Flux.fromIterable(routeLocators)
                .flatMap(RouteLocator::getRoutes)
                .collectMap(Route::getId)
                .block();
    }

    private Map.Entry<String, URI> route(String id, String uri) {
        return Map.entry(id, URI.create(uri));
    }
}
