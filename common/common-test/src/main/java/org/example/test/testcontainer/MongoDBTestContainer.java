package org.example.test.testcontainer;

import org.testcontainers.containers.MongoDBContainer;

public class MongoDBTestContainer extends MongoDBContainer {

    private static final String IMAGE_NAME = "mongo:6.0";

    public static MongoDBTestContainer container = (MongoDBTestContainer) new MongoDBTestContainer()
            .withReuse(true);

    public MongoDBTestContainer() {
        super(IMAGE_NAME);
    }
}
