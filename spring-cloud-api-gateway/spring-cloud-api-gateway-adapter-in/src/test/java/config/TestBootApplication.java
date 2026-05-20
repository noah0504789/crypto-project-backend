package config;

import org.example.common.properties.ApiPathProperties;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootConfiguration
@EnableConfigurationProperties(ApiPathProperties.class)
public class TestBootApplication {
}
