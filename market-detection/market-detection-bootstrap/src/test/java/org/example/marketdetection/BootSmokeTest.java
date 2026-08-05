package org.example.marketdetection;

import org.example.common.test.testcontainer.KafkaTestContainerInitializer;
import org.example.marketdetection.upbit.UpbitWebsocketClientStarter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 부팅 스모크: Config Server 없이 실제 git-config-repo 설정 + Testcontainers(kafka)로
 * ApplicationContext(Kafka Streams 바인더 포함)가 부팅되는지 검증한다.
 * ApplicationReadyEvent에서 외부 Upbit WebSocket에 접속하는 스타터는 mock으로 차단한다.
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

    @Autowired
    Environment environment;

    @MockitoBean
    UpbitWebsocketClientStarter upbitWebsocketClientStarter;

    @Test
    @DisplayName("ApplicationContext가 정상 부팅되고 Upbit ticker 출력은 native JSON serializer를 사용한다")
    void contextLoads() {
        assertThat(environment.getProperty(
                "spring.cloud.stream.default.producer.use-native-encoding",
                Boolean.class
        )).isTrue();
        assertThat(environment.getProperty(
                "spring.cloud.stream.kafka.bindings.upbitTickerEventSupplier-out-0.producer.configuration.value.serializer"
        )).isEqualTo("org.springframework.kafka.support.serializer.JsonSerializer");
    }
}
