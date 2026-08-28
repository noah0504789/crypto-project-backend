package org.example.websocket.gateway.adapter.in.websocket.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ExecutorConfigUnitTest {

    private static final int SINGLE_THREAD = 1;
    private static final int SINGLE_SLOT_QUEUE = 1;
    private static final long AWAIT_TIMEOUT_SECONDS = 5L;

    private final ExecutorConfig executorConfig = new ExecutorConfig();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    @DisplayName("팬아웃 풀은 큐가 포화되면 예외 없이 태스크를 버리고 거절 카운터만 올린다")
    void stompOutboundExecutor_whenQueueIsFull_shouldShedWithoutThrowing() throws InterruptedException {
        // given
        ThreadPoolTaskExecutor executor = executorConfig.stompOutboundExecutor(registry, saturatedProperties());
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            executor.execute(blockUntilReleased(running, release));
            assertThat(running.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            executor.execute(() -> {});

            // when
            // then
            assertThatCode(() -> executor.execute(() -> {})).doesNotThrowAnyException();
            assertThat(rejectedCount("outbound")).isEqualTo(1.0);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("ACK 풀은 큐가 포화되면 버리지 않고 호출 스레드에서 직접 실행한다")
    void chatMessageAckExecutor_whenQueueIsFull_shouldRunOnCallerThread() throws InterruptedException {
        // given
        ThreadPoolTaskExecutor executor = executorConfig.chatMessageAckExecutor(registry, saturatedProperties());
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> executedOn = new AtomicReference<>();
        try {
            executor.execute(blockUntilReleased(running, release));
            assertThat(running.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            executor.execute(() -> {});

            // when
            executor.execute(() -> executedOn.set(Thread.currentThread()));

            // then
            assertThat(executedOn.get()).isSameAs(Thread.currentThread());
            assertThat(registry.find("stomp.executor.rejected").tag("pool", "ack").counter()).isNull();
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("거절 카운터는 거절이 없어도 팬아웃 풀마다 0으로 미리 등록된다")
    void sheddingHandler_shouldPreRegisterCounterPerPool() {
        // given
        StompExecutorProperties properties = saturatedProperties();

        // when
        ThreadPoolTaskExecutor inbound = executorConfig.stompInboundExecutor(registry, properties);
        ThreadPoolTaskExecutor broker = executorConfig.stompBrokerExecutor(registry, properties);
        ThreadPoolTaskExecutor outbound = executorConfig.stompOutboundExecutor(registry, properties);

        // then
        try {
            assertThat(rejectedCount("inbound")).isEqualTo(0.0);
            assertThat(rejectedCount("broker")).isEqualTo(0.0);
            assertThat(rejectedCount("outbound")).isEqualTo(0.0);
        } finally {
            inbound.shutdown();
            broker.shutdown();
            outbound.shutdown();
        }
    }

    @Test
    @DisplayName("풀은 properties 값으로 만들어지고 core 스레드는 유휴로 종료되지 않는다")
    void newExecutor_shouldApplyPropertiesAndKeepCoreThreadsAlive() {
        // given
        StompExecutorProperties properties = new StompExecutorProperties(
                new StompExecutorProperties.Pool(2, 2, 11),
                new StompExecutorProperties.Pool(3, 3, 22),
                new StompExecutorProperties.Pool(4, 4, 33),
                new StompExecutorProperties.Pool(5, 5, 44)
        );

        // when
        ThreadPoolTaskExecutor inbound = executorConfig.stompInboundExecutor(registry, properties);
        ThreadPoolTaskExecutor broker = executorConfig.stompBrokerExecutor(registry, properties);
        ThreadPoolTaskExecutor outbound = executorConfig.stompOutboundExecutor(registry, properties);
        ThreadPoolTaskExecutor ack = executorConfig.chatMessageAckExecutor(registry, properties);

        // then
        try {
            assertPool(inbound, 2, 11);
            assertPool(broker, 3, 22);
            assertPool(outbound, 4, 33);
            assertPool(ack, 5, 44);
        } finally {
            inbound.shutdown();
            broker.shutdown();
            outbound.shutdown();
            ack.shutdown();
        }
    }

    private void assertPool(ThreadPoolTaskExecutor executor, int expectedPoolSize, int expectedQueueCapacity) {
        assertThat(executor.getCorePoolSize()).isEqualTo(expectedPoolSize);
        assertThat(executor.getMaxPoolSize()).isEqualTo(expectedPoolSize);
        assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(expectedQueueCapacity);
        assertThat(executor.getThreadPoolExecutor().allowsCoreThreadTimeOut()).isFalse();
    }

    private StompExecutorProperties saturatedProperties() {
        StompExecutorProperties.Pool pool =
                new StompExecutorProperties.Pool(SINGLE_THREAD, SINGLE_THREAD, SINGLE_SLOT_QUEUE);
        return new StompExecutorProperties(pool, pool, pool, pool);
    }

    private Runnable blockUntilReleased(CountDownLatch running, CountDownLatch release) {
        return () -> {
            running.countDown();
            try {
                release.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    // 거절 카운터는 kind 로도 나뉘므로 풀 단위 합계를 본다.
    private double rejectedCount(String poolName) {
        return registry.get("stomp.executor.rejected").tag("pool", poolName).counters().stream()
                .mapToDouble(Counter::count)
                .sum();
    }
}
