package org.example.apigateway.config;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
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
import java.util.List;
import java.util.Objects;

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

    // 특정 수치를 박아두지 않는다. 정책 값은 튜닝 대상이라(예: 팬아웃 측정을 위한 핸드셰이크
    // 한시 상향, TODO 1.14) 값을 고정하면 설정을 만질 때마다 테스트가 깨진다. 지켜야 하는 것은
    // 버킷이 전부 바인딩되는지와 record 에 선언된 제약을 운영 값이 만족하는지다.
    @Test
    @DisplayName("게이트웨이 Rate Limit 정책은 운영 설정에서 Bucket 단위로 바인딩되고 선언된 제약을 만족한다")
    void rateLimitPolicy_shouldBindFromGatewayConfig() throws IOException {
        StandardEnvironment environment = environmentWith("git-config-repo/dynamic/api-gateway.yml");

        GatewayRateLimitProperties properties = Binder.get(environment)
                .bind("gateway.rate-limit", Bindable.of(GatewayRateLimitProperties.class))
                .orElseThrow(() -> new IllegalStateException("Cannot bind gateway.rate-limit"));

        // Binder 는 Bean Validation 을 돌리지 않는다. 운영 설정에 직접 걸어봐야 의미가 있다.
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(properties)).isEmpty();
        }

        assertThat(buckets(properties))
                .noneMatch(Objects::isNull)
                .allSatisfy(bucket -> {
                    // 셋 다 양수여야 버킷이 돈다. replenishRate 가 0이면 토큰이 다시 차지 않는다.
                    assertThat(bucket.replenishRate()).isPositive();
                    assertThat(bucket.burstCapacity()).isPositive();
                    assertThat(bucket.requestedTokens()).isPositive();

                    // 한 번 요청이 쓰는 토큰이 버킷 용량보다 크면 어떤 요청도 통과하지 못한다.
                    assertThat(bucket.burstCapacity()).isGreaterThanOrEqualTo(bucket.requestedTokens());
                });
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

    private List<GatewayRateLimitProperties.Bucket> buckets(GatewayRateLimitProperties properties) {
        return List.of(
                properties.signUp(),
                properties.oauth2Authorization(),
                properties.oauth2Callback(),
                properties.tokenRefresh(),
                properties.logout(),
                properties.websocketHandshake(),
                properties.command(),
                properties.query(),
                properties.publicQuery()
        );
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
