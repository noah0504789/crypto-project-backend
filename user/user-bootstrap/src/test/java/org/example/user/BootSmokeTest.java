package org.example.user;

import org.example.common.test.testcontainer.KafkaTestContainerInitializer;
import org.example.common.test.testcontainer.ReadWriteMysqlTestContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * 부팅 스모크: Config Server 없이 실제 git-config-repo 설정 + Testcontainers(mysql R/W, kafka)로
 * ApplicationContext(JPA/gRPC 서버 포함)가 부팅되는지 검증한다. 스키마는 Hibernate가 생성한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                // imported user-service.yml(mode:always)을 이기려면 최상위 우선순위 프로퍼티로 덮는다.
                "spring.sql.init.mode=never",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ContextConfiguration(initializers = {
        ReadWriteMysqlTestContainerInitializer.class,
        KafkaTestContainerInitializer.class
})
class BootSmokeTest {

    @Test
    @DisplayName("ApplicationContext가 정상 부팅된다")
    void contextLoads() {
    }
}
