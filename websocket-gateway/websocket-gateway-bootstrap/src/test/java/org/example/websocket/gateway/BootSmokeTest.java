package org.example.websocket.gateway;

import org.example.common.test.testcontainer.KafkaTestContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * 부팅 스모크: Config Server 없이 실제 git-config-repo 설정(test resources application.yml에서 import) +
 * Testcontainers(redis/kafka)로 ApplicationContext가 끝까지 올라오는지 검증한다.
 * 자동설정/컴포넌트 스캔/@Conditional/빈 와이어링 실패를 CI가 잡는다(DB 없는 서비스).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = {
        KafkaTestContainerInitializer.class
})
class BootSmokeTest {

    @Test
    @DisplayName("ApplicationContext가 정상 부팅된다")
    void contextLoads() {
    }
}
