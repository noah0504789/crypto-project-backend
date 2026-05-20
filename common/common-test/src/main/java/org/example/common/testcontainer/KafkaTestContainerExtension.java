package org.example.common.testcontainer;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class KafkaTestContainerExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        KafkaTestContainer testcontainer = KafkaTestContainer.container;
        testcontainer.start();
        updateProps(testcontainer);
    }

    private void updateProps(KafkaTestContainer testcontainer) {
        System.setProperty("spring.cloud.stream.kafka.binder.brokers", testcontainer.getBootstrapServers());
    }
}
