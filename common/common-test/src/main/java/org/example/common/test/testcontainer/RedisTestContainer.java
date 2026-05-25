package org.example.common.test.testcontainer;

import org.testcontainers.containers.GenericContainer;

public class RedisTestContainer extends GenericContainer<RedisTestContainer> {

    private static final String IMAGE_NAME = "redis:7.2.0";
    public static final int PORT = 6379;

    public static RedisTestContainer container = new RedisTestContainer()
            .withExposedPorts(PORT)
            .withReuse(true);

    public RedisTestContainer() {
        super(IMAGE_NAME);
    }
}
