package org.example.marketdetection;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.common.test.testcontainer.KafkaTestContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ContextConfiguration;

/**
 * 부팅 스모크: Config Server 없이 실제 git-config-repo 설정 + Testcontainers(kafka)로 ApplicationContext(Kafka
 * Streams 바인더 포함)가 부팅되는지 검증한다. 수집은 upbit-connector가 담당하므로 외부 접속이 없다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.cloud.bus.enabled=false",
            "eureka.client.register-with-eureka=false",
            "eureka.client.fetch-registry=false",
            "grpc.server.port=0",
            "deployment.control-token=smoke-test",
            "spring.config.import="
                    + "optional:file:${smoke.config.repo}/application.yml,"
                    + "optional:file:${smoke.config.repo}/infrastructure/eureka-client.yml,"
                    + "optional:file:${smoke.config.repo}/infrastructure/kafka.yml,"
                    + "optional:file:${smoke.config.repo}/infrastructure/monitoring.yml,"
                    + "optional:file:${smoke.config.repo}/dynamic/market-detection.yml"
        })
@ContextConfiguration(initializers = KafkaTestContainerInitializer.class)
class BootSmokeTest {

    @Autowired Environment environment;

    @Test
    @DisplayName("ApplicationContext가 정상 부팅되고 ticker 소비는 upbit-ticker-event를 바라본다")
    void contextLoads() {
        assertThat(environment.getProperty("spring.cloud.stream.bindings.priceAlertDetectionProcessor-in-0.destination"))
                .isEqualTo("upbit-ticker-event");

        // 수집·발행은 upbit-connector로 이관됐다. 발행 바인딩이 남아 있으면 이중 발행이다.
        assertThat(environment.getProperty("spring.cloud.stream.bindings.upbitTickerEvent-out-0.destination"))
                .isNull();
    }
}
