package org.example.market;

import org.example.common.test.testcontainer.KafkaTestContainerInitializer;
import org.example.common.test.testcontainer.ReadWriteMysqlTestContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

/**
 * 부팅 스모크: Config Server 없이 실제 git-config-repo 설정 + Testcontainers(mysql R/W, kafka)로
 * ApplicationContext(JPA Read Replica·Snowflake ID·gRPC 서버 포함)가 부팅되는지 검증한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.sql.init.mode=never",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                // imported 서비스 설정의 고정 gRPC 포트를 이기고 랜덤 포트로 바인딩한다.
                "grpc.server.port=0"
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
