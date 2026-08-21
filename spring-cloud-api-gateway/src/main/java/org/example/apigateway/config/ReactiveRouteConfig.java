package org.example.apigateway.config;

import lombok.RequiredArgsConstructor;
import org.example.apigateway.ratelimit.GatewayKeyResolvers;
import org.example.apigateway.ratelimit.RateLimitedRouteId;
import org.example.common.properties.ApiPathProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

@Configuration
@RequiredArgsConstructor
public class ReactiveRouteConfig {

    private static final String OAUTH2_CLIENT_URI = "lb://oauth2-client";
    private static final String USER_SERVICE_URI = "lb://user-service";
    private static final String WEBSOCKET_GATEWAY_URI = "lb://websocket-gateway";
    private static final String WEBSOCKET_GATEWAY_WS_URI = "lb:ws://websocket-gateway";
    private static final String MARKET_SERVICE_URI = "lb://market-service";
    private static final String NOTIFICATION_SERVICE_URI = "lb://notification-service";
    private static final String CHAT_SERVICE_URI = "lb://chat-service";

    private final ApiPathProperties apiPathProperties;
    private final GatewayKeyResolvers keyResolvers;
    private final RedisRateLimiter rateLimiter;

    @Bean
    public RouteLocator oauth2ClientRoutes(RouteLocatorBuilder r) {
        return r.routes()
                .route(RateLimitedRouteId.OAUTH2_AUTHORIZATION, spec -> spec
                        .path(apiPathProperties.oauth2().authorizationPattern())
                        .and()
                        .method(HttpMethod.GET)
                        .filters(f -> oauth2Filters(rateLimit(f, keyResolvers.ip())))
                        .uri(OAUTH2_CLIENT_URI)
                )
                .route(RateLimitedRouteId.OAUTH2_CALLBACK, spec -> spec
                        .path(apiPathProperties.oauth2().loginCallbackBaseUri())
                        .and()
                        .method(HttpMethod.GET)
                        .filters(f -> oauth2Filters(rateLimit(f, keyResolvers.ip())))
                        .uri(OAUTH2_CLIENT_URI)
                )
                .route(RateLimitedRouteId.TOKEN_REFRESH, spec -> spec
                        .path(apiPathProperties.auth().refresh())
                        .and()
                        .method(HttpMethod.POST)
                        .filters(f -> oauth2Filters(rateLimit(f, keyResolvers.ip())))
                        .uri(OAUTH2_CLIENT_URI)
                )
                .route(RateLimitedRouteId.LOGOUT, spec -> spec
                        .path(apiPathProperties.auth().logout())
                        .and()
                        .method(HttpMethod.POST)
                        .filters(f -> oauth2Filters(rateLimit(f, keyResolvers.userOrIp())))
                        .uri(OAUTH2_CLIENT_URI)
                )
                .route("oauth2-client-route", spec -> spec
                        .path(
                                apiPathProperties.oauth2().pattern(),
                                apiPathProperties.oauth2().loginCallbackBaseUri(),
                                apiPathProperties.auth().pattern()
                        )
                        .filters(this::oauth2Filters)
                        .uri(OAUTH2_CLIENT_URI)
                )
                .build();
    }

    @Bean
    public RouteLocator userRoutes(RouteLocatorBuilder r) {
        return r.routes()
                .route(RateLimitedRouteId.USER_SIGN_UP, s -> s
                        .path(apiPathProperties.user().signUpPath())
                        .and()
                        .method(HttpMethod.POST)
                        .filters(f -> userFilters(rateLimit(f, keyResolvers.ip())))
                        .uri(USER_SERVICE_URI)
                )
                .route(RateLimitedRouteId.USER_COMMAND, s -> s
                        .path(apiPathProperties.user().mePath())
                        .and()
                        .method(HttpMethod.PATCH)
                        .filters(f -> userFilters(rateLimit(f, keyResolvers.user())))
                        .uri(USER_SERVICE_URI)
                )
                .route(RateLimitedRouteId.USER_QUERY, s -> s
                        .path(apiPathProperties.user().mePath(), apiPathProperties.user().profilePattern())
                        .and()
                        .method(HttpMethod.GET)
                        .filters(f -> userFilters(rateLimit(f, keyResolvers.user())))
                        .uri(USER_SERVICE_URI)
                )
                .route("user-route", s -> s
                        .path(apiPathProperties.user().pattern())
                        .filters(this::userFilters)
                        .uri(USER_SERVICE_URI)
                )
                .build();
    }

    @Bean
    public RouteLocator websocketGatewayRoutes(RouteLocatorBuilder r) {
        return r.routes()
                .route(RateLimitedRouteId.WEBSOCKET_NATIVE_HANDSHAKE, s -> s
                        .path(apiPathProperties.websocket().nativePath(), apiPathProperties.websocket().nativePattern())
                        .filters(f -> rateLimit(f, keyResolvers.user()))
                        .uri(WEBSOCKET_GATEWAY_WS_URI)
                )
                .route(RateLimitedRouteId.WEBSOCKET_HANDSHAKE, s -> s
                        .path(apiPathProperties.websocket().pattern())
                        .and()
                        .header("Upgrade", "(?i)websocket")
                        .filters(f -> rateLimit(f, keyResolvers.user()))
                        .uri(WEBSOCKET_GATEWAY_WS_URI)
                )
                .route("ws-http", s -> s
                        .path(
                                apiPathProperties.websocket().pattern(),
                                apiPathProperties.websocket().nativePath(),
                                apiPathProperties.websocket().nativePattern()
                        )
                        .filters(f -> f
                                .dedupeResponseHeader("Access-Control-Allow-Origin", "RETAIN_LAST")
                                .dedupeResponseHeader("Access-Control-Allow-Credentials", "RETAIN_LAST")
                                .dedupeResponseHeader("Vary", "RETAIN_UNIQUE")
                        )
                        .uri(WEBSOCKET_GATEWAY_URI)
                )
                .route("sockjs-route", spec -> spec
                        .path(apiPathProperties.websocket().msgPattern())
                        .uri(WEBSOCKET_GATEWAY_URI)
                )
                .build();
    }

    @Bean
    public RouteLocator marketRoutes(RouteLocatorBuilder r) {
        return r.routes()
                .route(RateLimitedRouteId.MARKET_COMMAND, s -> s
                        .path(apiPathProperties.market().priceAlerts(), apiPathProperties.market().priceAlertsPattern())
                        .and()
                        .method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)
                        .filters(f -> marketFilters(rateLimit(f, keyResolvers.user())))
                        .uri(MARKET_SERVICE_URI)
                )
                .route(RateLimitedRouteId.MARKET_QUERY, s -> s
                        .path(apiPathProperties.market().priceAlerts(), apiPathProperties.market().priceAlertsPattern())
                        .and()
                        .method(HttpMethod.GET)
                        .filters(f -> marketFilters(rateLimit(f, keyResolvers.user())))
                        .uri(MARKET_SERVICE_URI)
                )
                .route(RateLimitedRouteId.MARKET_PUBLIC_QUERY, s -> s
                        .path(apiPathProperties.market().markets(), apiPathProperties.market().marketsPattern())
                        .and()
                        .method(HttpMethod.GET)
                        .filters(f -> marketFilters(rateLimit(f, keyResolvers.ip())))
                        .uri(MARKET_SERVICE_URI)
                )
                .route("market-route", s -> s
                        .path(
                                apiPathProperties.market().markets(),
                                apiPathProperties.market().marketsPattern(),
                                apiPathProperties.market().priceAlerts(),
                                apiPathProperties.market().priceAlertsPattern()
                        )
                        .filters(this::marketFilters)
                        .uri(MARKET_SERVICE_URI)
                )
                .build();
    }

    @Bean
    public RouteLocator notificationRoutes(RouteLocatorBuilder r) {
        return r.routes()
                .route(RateLimitedRouteId.NOTIFICATION_COMMAND, s -> s
                        .path(apiPathProperties.notification().notificationsPattern())
                        .and()
                        .method(HttpMethod.PATCH)
                        .filters(f -> notificationFilters(rateLimit(f, keyResolvers.user())))
                        .uri(NOTIFICATION_SERVICE_URI)
                )
                .route(RateLimitedRouteId.NOTIFICATION_QUERY, s -> s
                        .path(apiPathProperties.notification().notificationsPattern())
                        .and()
                        .method(HttpMethod.GET)
                        .filters(f -> notificationFilters(rateLimit(f, keyResolvers.user())))
                        .uri(NOTIFICATION_SERVICE_URI)
                )
                .route("notification-route", s -> s
                        .path(
                                apiPathProperties.notification().notifications(),
                                apiPathProperties.notification().notificationsPattern()
                        )
                        .filters(this::notificationFilters)
                        .uri(NOTIFICATION_SERVICE_URI)
                )
                .build();
    }

    @Bean
    public RouteLocator chatRoutes(RouteLocatorBuilder r) {
        return r.routes()
                .route(RateLimitedRouteId.CHAT_COMMAND, s -> s
                        .path(
                                apiPathProperties.chat().roomCreate(),
                                apiPathProperties.chat().roomPattern(),
                                apiPathProperties.chat().roomMembersPattern(),
                                apiPathProperties.chat().roomActivityPattern()
                        )
                        .and()
                        .method(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)
                        .filters(f -> chatFilters(rateLimit(f, keyResolvers.user())))
                        .uri(CHAT_SERVICE_URI)
                )
                .route(RateLimitedRouteId.CHAT_QUERY, s -> s
                        .path(
                                apiPathProperties.chat().roomsMe(),
                                apiPathProperties.chat().roomPattern(),
                                apiPathProperties.chat().roomMePattern(),
                                apiPathProperties.chat().roomMessagesPattern()
                        )
                        .and()
                        .method(HttpMethod.GET)
                        .filters(f -> chatFilters(rateLimit(f, keyResolvers.user())))
                        .uri(CHAT_SERVICE_URI)
                )
                .route(RateLimitedRouteId.CHAT_PUBLIC_QUERY, s -> s
                        .path(apiPathProperties.chat().roomsPopular())
                        .and()
                        .method(HttpMethod.GET)
                        .filters(f -> chatFilters(rateLimit(f, keyResolvers.ip())))
                        .uri(CHAT_SERVICE_URI)
                )
                .route("chat-route", s -> s
                        .path(apiPathProperties.chat().pattern())
                        .filters(this::chatFilters)
                        .uri(CHAT_SERVICE_URI)
                )
                .build();
    }

    private GatewayFilterSpec rateLimit(GatewayFilterSpec filters, KeyResolver keyResolver) {
        return filters.requestRateLimiter(config -> config
                .setKeyResolver(keyResolver)
                .setRateLimiter(rateLimiter)
        );
    }

    private GatewayFilterSpec oauth2Filters(GatewayFilterSpec filters) {
        return filters
                .addRequestHeader("X-From", "gateway")
                .addResponseHeader("X-Gateway", "reactive");
    }

    private GatewayFilterSpec userFilters(GatewayFilterSpec filters) {
        return filters
                .addRequestHeader("X-From", "gateway")
                .rewritePath("/user(?<seg>/.*)?$", "/api/" + apiPathProperties.route().userApiVersion() + "/user${seg}")
                .addResponseHeader("X-Gateway", "reactive");
    }

    private GatewayFilterSpec marketFilters(GatewayFilterSpec filters) {
        return filters
                .addRequestHeader("X-From", "gateway")
                .rewritePath("/(?<seg>.*)", "/api/" + apiPathProperties.route().marketApiVersion() + "/${seg}")
                .addResponseHeader("X-Gateway", "reactive");
    }

    private GatewayFilterSpec notificationFilters(GatewayFilterSpec filters) {
        return filters
                .addRequestHeader("X-From", "gateway")
                .rewritePath("/(?<seg>.*)", "/api/" + apiPathProperties.route().notificationApiVersion() + "/${seg}")
                .addResponseHeader("X-Gateway", "reactive");
    }

    private GatewayFilterSpec chatFilters(GatewayFilterSpec filters) {
        return filters
                .addRequestHeader("X-From", "gateway")
                .rewritePath("/chat(?<seg>/.*)?$", "/api/" + apiPathProperties.route().chatApiVersion() + "/chat${seg}")
                .addResponseHeader("X-Gateway", "reactive");
    }
}
