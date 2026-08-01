package org.example.oauth2.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 부팅 스모크: Config Server 없이 실제 git-config-repo 설정으로 OAuth2 Client ApplicationContext가
 * 부팅되는지 검증한다. 외부 OIDC 디스커버리는 provider issuer-uri를 비우고 명시 엔드포인트로 대체해
 * 기동 시 외부 접속을 피한다(test application.yml 참고).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BootSmokeTest {

    @Test
    @DisplayName("ApplicationContext가 정상 부팅된다")
    void contextLoads() {
    }
}
