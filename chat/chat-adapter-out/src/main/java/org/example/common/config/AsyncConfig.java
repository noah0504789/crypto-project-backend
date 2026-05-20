//package org.example.common.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.scheduling.annotation.EnableAsync;
//import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
//
//import java.util.concurrent.Executor;
//import java.util.concurrent.ThreadPoolExecutor;
//
//@EnableAsync
//@Configuration
//public class AsyncConfig {
//
//    @Bean("retryExecutor")
//    public Executor retryExecutor() {
//        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
//        executor.setCorePoolSize(3);
//        executor.setMaxPoolSize(12);
//        executor.setQueueCapacity(1024);
//        executor.setThreadNamePrefix("async-retry-");
//
//        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
//
//        executor.initialize();
//
//        return executor;
//    }
//}
