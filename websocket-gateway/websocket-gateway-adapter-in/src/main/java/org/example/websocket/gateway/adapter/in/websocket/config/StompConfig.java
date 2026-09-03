package org.example.websocket.gateway.adapter.in.websocket.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.enums.HttpHeaderKey;
import org.example.common.properties.ApiPathProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
public class StompConfig implements WebSocketMessageBrokerConfigurer {

    private final ThreadPoolTaskExecutor stompBrokerExecutor;
    private final ThreadPoolTaskExecutor stompInboundExecutor;
    private final ThreadPoolTaskExecutor stompOutboundExecutor;
    private final ObjectMapper stompObjectMapper;
    private final ApiPathProperties apiPathProperties;

    public StompConfig(
            @Qualifier("stompBrokerExecutor") ThreadPoolTaskExecutor stompBrokerExecutor,
            @Qualifier("stompInboundExecutor") ThreadPoolTaskExecutor stompInboundExecutor,
            @Qualifier("stompOutboundExecutor") ThreadPoolTaskExecutor stompOutboundExecutor,
            ObjectMapper objectMapper,
            ApiPathProperties apiPathProperties
            ) {
        this.stompBrokerExecutor = stompBrokerExecutor;
        this.stompInboundExecutor = stompInboundExecutor;
        this.stompOutboundExecutor = stompOutboundExecutor;
        this.stompObjectMapper = objectMapper;
        this.apiPathProperties = apiPathProperties;
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
        resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);

        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(stompObjectMapper);
        converter.setContentTypeResolver(resolver);
        converter.setPrettyPrint(false);

        messageConverters.add(converter);

        return false;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
//        registry.setPreserveReceiveOrder(true);

        registry.addEndpoint(apiPathProperties.websocket().sockJsEndpoint())
                .setAllowedOriginPatterns("*")
                .setHandshakeHandler(handshakeHandler())
                .withSockJS();

        registry.addEndpoint(apiPathProperties.websocket().nativeEndpoint())
                .setHandshakeHandler(handshakeHandler())
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry
//                .setPreservePublishOrder(true) // produce 순서 보장
                .setApplicationDestinationPrefixes(apiPathProperties.stomp().applicationDestinationPrefix()) // 메시지 처리 prefix (@MessageMapping)
                .setUserDestinationPrefix(apiPathProperties.stomp().userDestinationPrefix())
                .setCacheLimit(1024 * 8)
                .enableSimpleBroker("/topic", "/queue"); // produce prefix

        registry.configureBrokerChannel()
                .taskExecutor(stompBrokerExecutor);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(stompInboundExecutor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(stompOutboundExecutor);
    }

    @Bean
    public ApplicationRunner disableWebsocketStatsLogging(WebSocketMessageBrokerStats stats) {
        return args -> stats.setLoggingPeriod(0);
    }

    private DefaultHandshakeHandler handshakeHandler() {
        return new DefaultHandshakeHandler() {
            @Override
            protected Principal determineUser(ServerHttpRequest req, WebSocketHandler h, Map<String,Object> attrs) {
                String userId = req.getHeaders().getFirst(HttpHeaderKey.USER_ID.value());
                if (userId == null || userId.isBlank()) throw new RuntimeException("Missing " + HttpHeaderKey.USER_ID.value());

                return () -> userId;
            }
        };
    }
}
