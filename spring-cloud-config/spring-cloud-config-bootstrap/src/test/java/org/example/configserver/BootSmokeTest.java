package org.example.configserver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.vault.core.VaultTemplate;

/**
 * 부팅 스모크: Config Server ApplicationContext가 부팅되는지 검증한다.
 * 실제 백엔드(git+Vault) 대신 native 백엔드로 로컬 git-config-repo를 서빙하고,
 * Transit 서명용 VaultTemplate은 mock으로 대체해 외부 Vault 접속 없이 부팅한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.vault.enabled=false",
                "spring.cloud.config.server.native.search-locations="
                        + "file:${smoke.config.repo},"
                        + "file:${smoke.config.repo}/dynamic,"
                        + "file:${smoke.config.repo}/infrastructure",
                "deployment.control-token=smoke-test"
        })
@ActiveProfiles("native")
class BootSmokeTest {

    @MockitoBean
    VaultTemplate vaultTemplate;

    @Test
    @DisplayName("ApplicationContext가 정상 부팅된다")
    void contextLoads() {
    }
}
