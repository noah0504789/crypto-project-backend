package org.example.apigateway.config;

import org.example.apigateway.ratelimit.GatewayRateLimitProperties;
import org.example.common.properties.ApiPathProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionApiPathConfigBindingUnitTest {

    private static final Path REPOSITORY_ROOT = findRepositoryRoot();

    @Test
    @DisplayName("공통 API 계약은 게이트웨이 ApiPathProperties에 기존 경로로 바인딩된다")
    void apiContract_shouldBindToGatewayApiPathProperties() throws IOException {
        StandardEnvironment environment = environmentWith(
                "git-config-repo/application.yml",
                "git-config-repo/dynamic/api-gateway.yml"
        );

        ApiPathProperties properties = Binder.get(environment)
                .bind("api-path", Bindable.of(ApiPathProperties.class))
                .orElseThrow(() -> new IllegalStateException("Cannot bind api-path"));

        assertThat(properties.user().mePath()).isEqualTo("/user/me/profile");
        assertThat(properties.auth().refresh()).isEqualTo("/auth/refresh");
        assertThat(properties.user().profilePattern()).isEqualTo("/user/*/profile");
        assertThat(properties.chat().roomsPopular()).isEqualTo("/chat/rooms/popular");
        assertThat(properties.chat().roomsMe()).isEqualTo("/chat/rooms/me");
        assertThat(properties.chat().roomMembersPattern()).isEqualTo("/chat/room/*/members");
        assertThat(properties.market().priceAlertsPattern()).isEqualTo("/price-alerts/**");
        assertThat(properties.notification().notificationsPattern()).isEqualTo("/notifications/**");
        assertThat(properties.oauth2().loginCallbackPattern()).isEqualTo("/login/oauth2/code/**");
        assertThat(properties.oauth2().authorizationPattern()).isEqualTo("/oauth2/authorization/**");
        assertThat(properties.websocket().msgPattern()).isEqualTo("/msg/**");
    }

    @Test
    @DisplayName("게이트웨이 Rate Limit 정책은 운영 설정에서 Bucket 단위로 바인딩된다")
    void rateLimitPolicy_shouldBindFromGatewayConfig() throws IOException {
        StandardEnvironment environment = environmentWith("git-config-repo/dynamic/api-gateway.yml");

        GatewayRateLimitProperties properties = Binder.get(environment)
                .bind("gateway.rate-limit", Bindable.of(GatewayRateLimitProperties.class))
                .orElseThrow(() -> new IllegalStateException("Cannot bind gateway.rate-limit"));

        assertThat(properties.signUp().replenishRate()).isEqualTo(5);
        assertThat(properties.signUp().requestedTokens()).isEqualTo(60);
        assertThat(properties.websocketHandshake().burstCapacity()).isEqualTo(5);
        assertThat(properties.query().replenishRate()).isEqualTo(10);
    }

    @Test
    @DisplayName("공통 API 계약은 Market과 Notification Controller 경로로 해석된다")
    void apiContract_shouldResolveControllerPaths() throws IOException {
        StandardEnvironment marketEnvironment = environmentWith(
                "git-config-repo/application.yml",
                "git-config-repo/dynamic/market-service.yml"
        );
        StandardEnvironment notificationEnvironment = environmentWith(
                "git-config-repo/application.yml",
                "git-config-repo/dynamic/notification-service.yml"
        );

        assertThat(marketEnvironment.getRequiredProperty("api-path.market.markets"))
                .isEqualTo("/markets");
        assertThat(marketEnvironment.getRequiredProperty("api-path.market.price-alerts-me"))
                .isEqualTo("/price-alerts/me");
        assertThat(notificationEnvironment.getRequiredProperty("api-path.notification.me"))
                .isEqualTo("/notifications/me");
        assertThat(notificationEnvironment.getRequiredProperty("api-path.notification.read"))
                .isEqualTo("/notifications/{notificationId}/read");
    }

    private StandardEnvironment environmentWith(String... configPaths) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

        for (String configPath : configPaths) {
            FileSystemResource resource = new FileSystemResource(REPOSITORY_ROOT.resolve(configPath));
            loader.load(configPath, resource)
                    .forEach(environment.getPropertySources()::addLast);
        }

        return environment;
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();

        while (current != null) {
            if (Files.exists(current.resolve("git-config-repo/application.yml"))) {
                return current;
            }
            current = current.getParent();
        }

        throw new IllegalStateException("Cannot find repository root");
    }
}
