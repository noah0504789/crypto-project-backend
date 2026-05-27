package org.example.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "org.example")
@ConfigurationPropertiesScan(basePackages = "org.example")
public class Main {
    public static void main(String[] args) {
        new SpringApplication(Main.class).run(args);
    }
}
