package org.example.websocket.gateway.adapter.in.websocket.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.common.enums.StompDestination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHandlingRunnable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.RejectedExecutionHandler;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("거절된 STOMP 태스크의 목적지 분류")
class ExecutorConfigRejectionKindUnitTest {

    private MeterRegistry registry;
    private RejectedExecutionHandler handler;
    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();

        StompExecutorProperties properties = new StompExecutorProperties(
                new StompExecutorProperties.Pool(1, 1, 1),
                new StompExecutorProperties.Pool(1, 1, 1),
                new StompExecutorProperties.Pool(1, 1, 1),
                new StompExecutorProperties.Pool(1, 1, 1)
        );

        executor = new ExecutorConfig().stompBrokerExecutor(registry, properties);
        handler = executor.getThreadPoolExecutor().getRejectedExecutionHandler();
    }

    private double rejected(String kind) {
        return registry.counter("stomp.executor.rejected", "pool", "broker", "kind", kind).count();
    }

    private void reject(String destination) {
        Message<byte[]> message = MessageBuilder
                .withPayload(new byte[0])
                .setHeader(SimpMessageHeaderAccessor.DESTINATION_HEADER, destination)
                .build();

        handler.rejectedExecution(new StubTask(message), executor.getThreadPoolExecutor());
    }

    @Test
    @DisplayName("채팅 브로드캐스트는 broadcast 로 센다")
    void classifiesBroadcast() {
        reject(StompDestination.CHAT_ROOM_PREFIX.destination("room-1"));

        assertThat(rejected("broadcast")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("ACK 는 세션 접미사가 붙어도 ack 로 센다")
    void classifiesAckWithSessionSuffix() {
        reject("/queue/chat/ack-usersession123");

        assertThat(rejected("ack")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("뱃지는 badge 로 센다")
    void classifiesBadge() {
        reject("/queue/chat/badge-usersession123");

        assertThat(rejected("badge")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("알림은 세션 접미사가 붙어도 notification 으로 센다")
    void classifiesNotificationWithSessionSuffix() {
        reject("/queue/notification-usersession123");

        assertThat(rejected("notification")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("목적지 헤더가 없으면 none 으로 센다")
    void classifiesMissingDestination() {
        handler.rejectedExecution(
                new StubTask(MessageBuilder.withPayload(new byte[0]).build()),
                executor.getThreadPoolExecutor()
        );

        assertThat(rejected("none")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("메시지를 꺼낼 수 없는 태스크는 unknown 으로 센다")
    void classifiesNonMessageTask() {
        handler.rejectedExecution(() -> {
        }, executor.getThreadPoolExecutor());

        assertThat(rejected("unknown")).isEqualTo(1.0);
    }

    private record StubTask(Message<?> message) implements MessageHandlingRunnable {

        @Override
        public void run() {
        }

        @Override
        public Message<?> getMessage() {
            return message;
        }

        @Override
        public MessageHandler getMessageHandler() {
            return m -> {
            };
        }
    }
}
