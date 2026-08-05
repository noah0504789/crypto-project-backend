package org.example.common.test.testcontainer;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class ReadWriteMysqlTestContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final MySQLContainer<?> WRITE_MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("write_db")
            .withUsername("root")
            .withPassword("rootpass")
            .withReuse(true);

    private static final MySQLContainer<?> READ_MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("read_db")
            .withUsername("root")
            .withPassword("rootpass")
            .withReuse(true);

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        WRITE_MYSQL.start();
        READ_MYSQL.start();
        createEventCatalog();

        TestPropertyValues.of(
                "spring.datasource.write.url=" + WRITE_MYSQL.getJdbcUrl(),
                "spring.datasource.write.username=" + WRITE_MYSQL.getUsername(),
                "spring.datasource.write.password=" + WRITE_MYSQL.getPassword(),
                "spring.datasource.write.driver-class-name=" + WRITE_MYSQL.getDriverClassName(),
                "spring.datasource.read.url=" + READ_MYSQL.getJdbcUrl(),
                "spring.datasource.read.username=" + READ_MYSQL.getUsername(),
                "spring.datasource.read.password=" + READ_MYSQL.getPassword(),
                "spring.datasource.read.driver-class-name=" + READ_MYSQL.getDriverClassName(),
                "spring.datasource.write.hikari.maximum-pool-size=3",
                "spring.datasource.read.hikari.maximum-pool-size=3",
                "spring.test.database.replace=none"
        ).applyTo(applicationContext.getEnvironment());
    }

    public static MySQLContainer<?> writeMysql() {
        return WRITE_MYSQL;
    }

    public static MySQLContainer<?> readMysql() {
        return READ_MYSQL;
    }

    private static void createEventCatalog() {
        try (
                Connection connection = DriverManager.getConnection(
                        WRITE_MYSQL.getJdbcUrl(),
                        WRITE_MYSQL.getUsername(),
                        WRITE_MYSQL.getPassword()
                );
                Statement statement = connection.createStatement()
        ) {
            statement.execute("CREATE DATABASE IF NOT EXISTS event");
        } catch (SQLException e) {
            throw new IllegalStateException("failed to create event catalog for smoke test", e);
        }
    }
}
