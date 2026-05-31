package org.example.apigateway.config;

import lombok.RequiredArgsConstructor;
import org.example.common.properties.ApiPathProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ReactiveRouteConfig {

    private final ApiPathProperties apiPathProperties;

    @Bean
    public RouteLocator oauth2ClientRoutes(RouteLocatorBuilder r) {
        return r.routes()
                .route("oauth2-client-route", spec -> spec
                        .path(
                                apiPathProperties.oauth2().pattern(),
                                apiPathProperties.oauth2().loginCallbackBaseUri(),
                                apiPathProperties.auth().pattern()
                        )
                        .filters(f -> f
                                .addRequestHeader("X-From", "gateway")
                                .addResponseHeader("X-Gateway", "reactive")
                        )
                        .uri("lb://oauth2-client")
                )
                .build();
    }

    @Bean
    public RouteLocator userRoutes(RouteLocatorBuilder r) {
        return r.routes()
                .route("user-route", s -> s
                        .path(apiPathProperties.user().pattern())
                        .filters(f -> f
                                .addRequestHeader("X-From", "gateway")
                                .rewritePath("/user(?<seg>/.*)?$", "/api/" + apiPathProperties.route().userApiVersion() + "/user${seg}")
                                .addResponseHeader("X-Gateway", "reactive"))
                        .uri("lb://user-service")
                )
                .build();
    }

    @Bean
    public RouteLocator websocketGatewayRoutes(RouteLocatorBuilder r) {
        return r.routes()
                .route("ws-native-upgrade", s -> s
                        .path(apiPathProperties.websocket().nativePath(), apiPathProperties.websocket().nativePattern())
                        .uri("lb:ws://websocket-gateway")
                )
                .route("ws-upgrade", s -> s
                        .path(apiPathProperties.websocket().pattern())
                        .and()
                        .header("Upgrade", "(?i)websocket")
                        .uri("lb:ws://websocket-gateway")
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
                        .uri("lb://websocket-gateway")
                )
                .route("sockjs-route", spec -> spec
                        .path(apiPathProperties.websocket().msgPattern())
                        .uri("lb://websocket-gateway")
                )
                .build();
    }

    @Bean
    public RouteLocator chatRoutes(RouteLocatorBuilder r) {
        return r.routes()
                .route("chat-route", s -> s
                        .path(apiPathProperties.chat().pattern())
                        .filters(f -> f
                                .addRequestHeader("X-From", "gateway")
                                .rewritePath("/chat(?<seg>/.*)?$", "/api/" + apiPathProperties.route().chatApiVersion() + "/chat${seg}")
                                .addResponseHeader("X-Gateway", "reactive")
                        )
                        .uri("lb://chat-service")
                )
                .build();
    }
}
