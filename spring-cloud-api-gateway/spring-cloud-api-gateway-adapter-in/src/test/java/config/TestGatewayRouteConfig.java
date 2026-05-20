package config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestGatewayRouteConfig {

    @Bean
    public RouteLocator testOauth2ClientRoutes(RouteLocatorBuilder r, TestDownstreamServerConfig.TestDownstreamServers downstreamServers) {
        return r.routes()
                .route("oauth2-client-route", spec -> spec
                        .path("/oauth2/**", "/login/oauth2/code/*", "/auth/**")
                        .filters(f -> f
                                .addRequestHeader("X-From", "gateway")
                                .addResponseHeader("X-Gateway", "reactive")
                        )
                        .uri("http://localhost:" + downstreamServers.oauth2ClientPort())
                )
                .build();
    }

    @Bean
    public RouteLocator testUserRoutes(RouteLocatorBuilder r, TestDownstreamServerConfig.TestDownstreamServers downstreamServers) {
        return r.routes()
                .route("user-route", s -> s
                        .path("/user/**")
                        .filters(f -> f
                                .addRequestHeader("X-From", "gateway")
                                .rewritePath("/user(?<seg>/.*)?$", "/api/v1/user${seg}")
                                .addResponseHeader("X-Gateway", "reactive"))
                        .uri("http://localhost:" + downstreamServers.userPort())
                )
                .build();
    }

    @Bean
    public RouteLocator testChatRoutes(RouteLocatorBuilder r, TestDownstreamServerConfig.TestDownstreamServers downstreamServers) {
        return r.routes()
                .route("chat-route", s -> s
                        .path("/chat/**")
                        .filters(f -> f
                                .addRequestHeader("X-From", "gateway")
                                .rewritePath("/chat(?<seg>/.*)?$", "/api/v1/chat${seg}")
                                .addResponseHeader("X-Gateway", "reactive")
                        )
                        .uri("http://localhost:" + downstreamServers.chatPort())
                )
                .build();
    }
}