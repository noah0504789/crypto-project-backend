package config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;

import java.util.List;

@TestConfiguration
public class TestLoadBalancerConfig {

    private static final String USER_SERVICE_ID = "user-service";
    private static final String CHAT_SERVICE_ID = "chat-service";
    private static final String OAUTH2_CLIENT_SERVICE_ID = "oauth2-client";

    @Bean
    public ServiceInstanceListSupplier userServiceInstanceListSupplier(TestDownstreamServerConfig.TestDownstreamServers servers) {
        return serviceInstanceListSupplier(USER_SERVICE_ID, servers.userPort());
    }

    @Bean
    public ServiceInstanceListSupplier chatServiceInstanceListSupplier(TestDownstreamServerConfig.TestDownstreamServers servers) {
        return serviceInstanceListSupplier(CHAT_SERVICE_ID, servers.chatPort());
    }

    @Bean
    public ServiceInstanceListSupplier oauth2ClientServiceInstanceListSupplier(TestDownstreamServerConfig.TestDownstreamServers servers) {
        return serviceInstanceListSupplier(OAUTH2_CLIENT_SERVICE_ID, servers.oauth2ClientPort());
    }

    private ServiceInstanceListSupplier serviceInstanceListSupplier(String serviceId, int port) {
        return new ServiceInstanceListSupplier() {
            @Override
            public String getServiceId() {
                return serviceId;
            }

            @Override
            public Flux<List<ServiceInstance>> get() {
                return Flux.just(List.of(
                        new DefaultServiceInstance(
                                serviceId + "-1",
                                serviceId,
                                "localhost",
                                port,
                                false
                        )
                ));
            }
        };
    }
}