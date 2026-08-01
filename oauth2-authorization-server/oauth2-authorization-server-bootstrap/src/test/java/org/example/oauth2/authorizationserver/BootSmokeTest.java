package org.example.oauth2.authorizationserver;

import org.example.common.test.testcontainer.KafkaTestContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * 부팅 스모크: Config Server 없이 실제 git-config-repo 설정 + Testcontainers(kafka)로
 * Authorization Server ApplicationContext(gRPC 서버 포함)가 부팅되는지 검증한다.
 * DB 없음, redis 클러스터는 lazy, Vault 위임 서명은 HTTP lazy. RegisteredClient용 시크릿은 더미로 채운다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "grpc.server.port=0",
                // RegisteredClient가 기동 시 client secret을 해시하므로 더미 값을 채운다(원래는 Vault 시크릿).
                "my.client-id=smoke-client",
                "my.client-secret=smoke-secret",
                "my.client-registration-id=smoke-registration"
        })
@ContextConfiguration(initializers = KafkaTestContainerInitializer.class)
class BootSmokeTest {

    @Test
    @DisplayName("ApplicationContext가 정상 부팅된다")
    void contextLoads() {
    }
}
