package org.example.outboxpoller;

import org.example.common.test.testcontainer.KafkaTestContainerInitializer;
import org.example.common.test.testcontainer.ReadWriteMysqlTestContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 부팅 스모크: Config Server 없이 실제 git-config-repo 설정 + Testcontainers(mysql R/W, kafka)로
 * Outbox/DLQ 릴레이 폴러 ApplicationContext가 부팅되는지 검증한다. 스키마는 Hibernate가 생성한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.sql.init.mode=never",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ContextConfiguration(initializers = {
        ReadWriteMysqlTestContainerInitializer.class,
        KafkaTestContainerInitializer.class
})
class BootSmokeTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("ApplicationContext가 정상 부팅된다")
    void contextLoads() {
        var response = restTemplate.getForEntity("/actuator/health/liveness", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }
}
