package org.example.websocket.gateway.websocket.adapter.out.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MonitorConfig {

    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags(
            @Value("${spring.cloud.stream.instance-index:unknown}") String instanceIndex
    ) {
        return registry -> registry.config().commonTags("instanceIndex", instanceIndex);
    }
}
