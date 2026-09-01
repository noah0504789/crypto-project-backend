package org.example.websocket.gateway.websocket.adapter.out.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

@Configuration
public class OutboundSchedulerConfig {

    @Bean(name = "chatMessageBatchScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService chatMessageBatchScheduler() {
        return Executors.newSingleThreadScheduledExecutor(daemonThreadFactory("chat-batch-"));
    }

    @Bean(name = "badgeCoalesceScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService badgeCoalesceScheduler() {
        return Executors.newSingleThreadScheduledExecutor(daemonThreadFactory("badge-coalesce-"));
    }

    private ThreadFactory daemonThreadFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}
