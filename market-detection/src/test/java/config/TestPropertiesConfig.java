package config;

import org.example.marketdetection.infra.properties.UpbitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
@EnableConfigurationProperties({
        UpbitProperties.class
})
public class TestPropertiesConfig {
}