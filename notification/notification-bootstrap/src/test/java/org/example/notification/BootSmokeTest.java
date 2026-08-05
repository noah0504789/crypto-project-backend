package org.example.notification;

import org.example.common.test.testcontainer.KafkaTestContainerInitializer;
import org.example.common.test.testcontainer.MongoDBTestContainerInitializer;
import org.example.common.test.testcontainer.ReadWriteMysqlTestContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 부팅 스모크: Config Server 없이 실제 git-config-repo 설정 + Testcontainers(mysql R/W, mongo, kafka)로
 * ApplicationContext(Outbox JPA + Mongo 알림 저장 포함)가 부팅되는지 검증한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.sql.init.mode=never",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "grpc.server.port=0",
                // MongoConfig가 읽는 db 이름을 Testcontainer 기본 db(test)로 맞춘다(원래는 시크릿 체인).
                "mongo.db=test"
        })
@ContextConfiguration(initializers = {
        ReadWriteMysqlTestContainerInitializer.class,
        MongoDBTestContainerInitializer.class,
        KafkaTestContainerInitializer.class
})
class BootSmokeTest {

    @Autowired
    Environment environment;

    @Test
    @DisplayName("ApplicationContext가 정상 부팅되고 Kafka JSON 이벤트 패키지를 신뢰한다")
    void contextLoads() {
        assertThat(environment.getProperty(
                "spring.cloud.stream.kafka.binder.consumer-properties.spring.json.trusted.packages"
        )).isEqualTo("*");
    }
}
