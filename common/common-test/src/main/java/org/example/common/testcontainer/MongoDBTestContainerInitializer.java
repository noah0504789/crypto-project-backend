package org.example.common.testcontainer;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class MongoDBTestContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        MongoDBTestContainer container = MongoDBTestContainer.container;
        container.start();

        String uri = container.getReplicaSetUrl();

        System.setProperty("spring.data.mongodb.uri", uri);

        TestPropertyValues.of(
                "spring.data.mongodb.uri=" + uri
        ).applyTo(applicationContext.getEnvironment());
    }
}
