package org.example.websocket.adapter.out.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InstanceInfoLogger implements ApplicationRunner {

    @Value("${spring.cloud.stream.instance-index:unknown}")
    private String instanceIndex;

    @Override
    public void run(ApplicationArguments args) {
        log.info("websocket-gateway instance-index={}", instanceIndex);
    }
}
