package org.example.upbitconnector.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.http.client.HttpClient;

@Configuration
public class UpbitHttpClientConfig {

    @Bean
    public HttpClient upbitHttpClient() {
        return HttpClient.create();
    }
}
