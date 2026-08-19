package org.example.upbitconnector;

import org.example.common.test.testcontainer.KafkaTestContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * 부팅 스모크: Config Server 없이 실제 git-config-repo 설정 + Testcontainers(kafka)로 WebFlux
 * ApplicationContext가 부팅되는지 검증한다. 실제 Upbit 접속은 {@code upbit.websocket.enabled=false}로 차단한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = KafkaTestContainerInitializer.class)
class BootSmokeTest {

    @Test
    @DisplayName("ApplicationContext가 정상 부팅된다")
    void contextLoads() {}
}
