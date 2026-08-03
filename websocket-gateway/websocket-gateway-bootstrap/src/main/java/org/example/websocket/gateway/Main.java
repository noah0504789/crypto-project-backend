package org.example.websocket.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

// DB가 없는 서비스라 계약 전이로 딸려온 common 영속 서비스 빈(common-outbox/-dlq/-inbox의
// OutboxService·DlqService·InboxService 등)은 스캔에서 제외한다. 이 서비스는 이벤트 클래스만
// 소비하고 outbox 발행/DLQ/Inbox는 쓰지 않으므로, 스캔하면 JPA Repository 빈을 요구해 부팅이 실패한다.
@SpringBootApplication
@ComponentScan(
        basePackages = "org.example",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "org\\.example\\.common\\.(outbox|dlq|inbox)\\..*"))
@ConfigurationPropertiesScan(basePackages = "org.example")
public class Main {
    public static void main(String[] args) {
        new SpringApplication(Main.class).run(args);
    }
}
