package org.example.apigateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 부팅 스모크: Config Server 없이(기존 test application.yml이 설정을 제공) 전체 Gateway(WebFlux)
 * ApplicationContext가 부팅되는지 검증한다. JWKS/디스커버리는 lazy라 외부 인프라 의존이 없다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BootSmokeTest {

    @Test
    @DisplayName("ApplicationContext가 정상 부팅된다")
    void contextLoads() {
    }
}
