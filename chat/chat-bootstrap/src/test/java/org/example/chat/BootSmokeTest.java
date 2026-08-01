package org.example.chat;

import org.example.common.test.testcontainer.KafkaTestContainerInitializer;
import org.example.common.test.testcontainer.MongoDBTestContainerInitializer;
import org.example.common.test.testcontainer.ReadWriteMysqlTestContainerInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 부팅 스모크: Config Server 없이 실제 git-config-repo 설정 + Testcontainers(mysql R/W, mongo, kafka)로
 * ApplicationContext(JPA Outbox + Mongo 채팅 저장 + gRPC 서버 포함)가 부팅되는지 검증한다.
 * eager로 redis 클러스터에 접속하는 RedissonClient는 mock으로 차단하고, lettuce 커넥션은 lazy다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.sql.init.mode=never",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "grpc.server.port=0",
                "mongo.db=test"
        })
@ContextConfiguration(initializers = {
        ReadWriteMysqlTestContainerInitializer.class,
        MongoDBTestContainerInitializer.class,
        KafkaTestContainerInitializer.class
})
class BootSmokeTest {

    @MockitoBean
    RedissonClient redissonClient;

    @Test
    @DisplayName("ApplicationContext가 정상 부팅된다")
    void contextLoads() {
    }
}
