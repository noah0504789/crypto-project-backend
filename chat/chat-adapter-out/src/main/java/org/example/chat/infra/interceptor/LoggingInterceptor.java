package org.example.chat.infra.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.example.common.enums.KafkaHeaderKey;
import org.springframework.integration.config.GlobalChannelInterceptor;
import org.springframework.integration.support.context.NamedComponent;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@GlobalChannelInterceptor(patterns = {"*Producer*", "*Consumer*"})
@Component
public class LoggingInterceptor implements ChannelInterceptor {

    @Override
    public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
        String correlactionId = message.getHeaders().get(KafkaHeaderKey.TRANSACTION_ID.value())+"";
        Object payload = message.getPayload();
        String body = payload instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : payload.toString();

        log.debug("[전송 후] 채널={}, txid={}, 바디={}", getChannelName(channel), correlactionId, body);
    }

    private String getChannelName(MessageChannel channel) {
        return channel instanceof NamedComponent named ? named.getComponentName() : channel.toString();
    }
}
