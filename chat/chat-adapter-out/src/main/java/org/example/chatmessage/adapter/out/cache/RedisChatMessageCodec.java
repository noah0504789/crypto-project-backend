package org.example.chatmessage.adapter.out.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.chatmessage.domain.model.ChatMessage;
import org.example.chat.common.exception.ChatCacheException;
import org.example.common.redis.codec.RedisValueCodec;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisChatMessageCodec implements RedisValueCodec<ChatMessage> {

    private final ObjectMapper objectMapper;

    @Override
    public String write(ChatMessage value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ChatCacheException("[redis] chatmessage 직렬화 실패", e);
        }
    }

    @Override
    public ChatMessage read(String source) {
        try {
            return objectMapper.readValue(source, ChatMessage.class);
        } catch (JsonProcessingException e) {
            throw new ChatCacheException("[redis] chatmessage 직렬화 실패", e);
        }
    }
}
