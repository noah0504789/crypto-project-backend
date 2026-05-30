package org.example.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.config.server.EnableConfigServer;

@EnableConfigServer
@SpringBootApplication(scanBasePackages = "org.example")
@ConfigurationPropertiesScan(basePackages = "org.example")
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
