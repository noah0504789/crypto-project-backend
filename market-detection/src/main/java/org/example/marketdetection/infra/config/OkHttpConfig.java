package org.example.marketdetection.infra.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class OkHttpConfig {

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient().newBuilder()
                .readTimeout(10, TimeUnit.SECONDS)
                .connectTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }
}
