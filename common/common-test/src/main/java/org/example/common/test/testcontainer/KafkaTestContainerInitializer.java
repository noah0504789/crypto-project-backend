package org.example.common.test.testcontainer;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class KafkaTestContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        KafkaTestContainer container = KafkaTestContainer.container;
        container.start();

        String brokers = container.getBootstrapServers();
        System.setProperty("spring.cloud.stream.kafka.binder.brokers", brokers);
        System.setProperty("spring.kafka.bootstrap-servers", brokers);

        TestPropertyValues.of(
                "spring.cloud.stream.kafka.binder.brokers=" + brokers,
                "spring.kafka.bootstrap-servers=" + brokers,
                "spring.cloud.stream.kafka.streams.binder.brokers=" + brokers
        ).applyTo(applicationContext.getEnvironment());
    }
}
