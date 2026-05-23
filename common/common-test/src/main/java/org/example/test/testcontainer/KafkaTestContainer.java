package org.example.test.testcontainer;

import org.testcontainers.kafka.KafkaContainer;

public class KafkaTestContainer extends KafkaContainer {

    private static final String IMAGE_NAME = "apache/kafka-native:3.8.0";

    public static KafkaTestContainer container = (KafkaTestContainer) new KafkaTestContainer()
            .withReuse(true);

    public KafkaTestContainer() {
        super(IMAGE_NAME);
    }
}
