package org.example.apigateway.config;

import java.util.Map;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.LoadBalancerLifecycle;
import org.springframework.cloud.client.loadbalancer.LoadBalancerProperties;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
public class TestLoadBalancerClientFactoryConfig {

    private static final String USER_SERVICE_ID = "user-service";

    @Bean
    @Primary
    @SuppressWarnings({"rawtypes", "unchecked"})
    public LoadBalancerClientFactory loadBalancerClientFactory(
            TestDownstreamServerConfig.TestDownstreamServers servers
    ) {
        LoadBalancerClientFactory factory = mock(LoadBalancerClientFactory.class);
        ServiceInstance instance = new DefaultServiceInstance(
                USER_SERVICE_ID + "-1",
                USER_SERVICE_ID,
                "localhost",
                servers.userPort(),
                false
        );
        ReactorServiceInstanceLoadBalancer loadBalancer = request -> Mono.just(new DefaultResponse(instance));

        when(factory.getInstance(USER_SERVICE_ID, ReactorServiceInstanceLoadBalancer.class))
                .thenReturn(loadBalancer);
        when(factory.getInstances(USER_SERVICE_ID, LoadBalancerLifecycle.class))
                .thenReturn(Map.of());
        when(factory.getProperties(USER_SERVICE_ID))
                .thenReturn(new LoadBalancerProperties());
        return factory;
    }
}
