package org.example.apigateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpHeaderNames;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@TestConfiguration
public class TestDownstreamServerConfig {

    @Bean
    public TestDownstreamServers downstreamServers(ObjectMapper objectMapper) {
        return new TestDownstreamServers(objectMapper);
    }

    public static class TestDownstreamServers {

        private static final String USER_ID_HEADER = "X-User-Id";
        private static final String FROM_HEADER = "X-From";

        private final AtomicReference<CapturedRequest> lastUserRequest = new AtomicReference<>();
        private final AtomicReference<CapturedRequest> lastChatRequest = new AtomicReference<>();
        private final AtomicReference<CapturedRequest> lastOauth2ClientRequest = new AtomicReference<>();
        private final AtomicInteger userRequestCount = new AtomicInteger();
        private final AtomicInteger chatRequestCount = new AtomicInteger();
        private final AtomicInteger oauth2ClientRequestCount = new AtomicInteger();

        private final DisposableServer userServer;
        private final DisposableServer chatServer;
        private final DisposableServer oauth2ClientServer;

        public TestDownstreamServers(ObjectMapper objectMapper) {
            this.userServer = startServer("user-service", lastUserRequest, userRequestCount, objectMapper);
            this.chatServer = startServer("chat-service", lastChatRequest, chatRequestCount, objectMapper);
            this.oauth2ClientServer = startServer(
                    "oauth2-client",
                    lastOauth2ClientRequest,
                    oauth2ClientRequestCount,
                    objectMapper
            );
        }

        private DisposableServer startServer(
                String serviceName,
                AtomicReference<CapturedRequest> holder,
                AtomicInteger requestCount,
                ObjectMapper objectMapper
        ) {
            return HttpServer.create()
                    .host("localhost")
                    .port(0)
                    .handle((request, response) -> {
                        CapturedRequest capturedRequest = new CapturedRequest(
                                request.method().name(),
                                request.uri(),
                                request.requestHeaders().get(USER_ID_HEADER),
                                request.requestHeaders().get(FROM_HEADER)
                        );

                        holder.set(capturedRequest);
                        requestCount.incrementAndGet();

                        Map<String, Object> body = Map.of(
                                "service", serviceName,
                                "method", capturedRequest.method(),
                                "path", capturedRequest.path(),
                                "xUserId", capturedRequest.xUserId() == null ? "" : capturedRequest.xUserId(),
                                "xFrom", capturedRequest.xFrom() == null ? "" : capturedRequest.xFrom()
                        );

                        byte[] bytes;
                        try {
                            bytes = objectMapper.writeValueAsBytes(body);
                        } catch (Exception e) {
                            bytes = "{}".getBytes(StandardCharsets.UTF_8);
                        }

                        return response
                                .status(200)
                                .header(HttpHeaderNames.CONTENT_TYPE, "application/json")
                                .sendByteArray(Mono.just(bytes));
                    })
                    .bindNow();
        }

        public int userPort() {
            return userServer.port();
        }

        public int chatPort() {
            return chatServer.port();
        }

        public int oauth2ClientPort() {
            return oauth2ClientServer.port();
        }

        public CapturedRequest lastUserRequest() {
            return lastUserRequest.get();
        }

        public CapturedRequest lastChatRequest() {
            return lastChatRequest.get();
        }

        public CapturedRequest lastOauth2ClientRequest() {
            return lastOauth2ClientRequest.get();
        }

        public int userRequestCount() {
            return userRequestCount.get();
        }

        public void reset() {
            lastUserRequest.set(null);
            lastChatRequest.set(null);
            lastOauth2ClientRequest.set(null);
            userRequestCount.set(0);
            chatRequestCount.set(0);
            oauth2ClientRequestCount.set(0);
        }

        @PreDestroy
        public void dispose() {
            userServer.disposeNow();
            chatServer.disposeNow();
            oauth2ClientServer.disposeNow();
        }

        public record CapturedRequest(
                String method,
                String path,
                String xUserId,
                String xFrom
        ) {
        }
    }
}
