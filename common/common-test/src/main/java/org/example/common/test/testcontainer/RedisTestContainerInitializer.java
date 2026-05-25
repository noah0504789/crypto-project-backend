package org.example.common.test.testcontainer;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class RedisTestContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        RedisTestContainer container = RedisTestContainer.container;
        container.start();

        String host = container.getHost();
        String port = container.getMappedPort(RedisTestContainer.PORT).toString();

        System.setProperty("spring.data.redis.host", host);
        System.setProperty("spring.data.redis.port", port);

        TestPropertyValues.of(
                "spring.data.redis.host=" + host,
                "spring.data.redis.port=" + port,
                "spring.redis.host=" + host,
                "spring.redis.port=" + port
        ).applyTo(applicationContext.getEnvironment());
    }
}
