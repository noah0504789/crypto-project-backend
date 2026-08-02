package org.example.notification.adapter.out.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.common.redis.codec.RedisHashCodec;
import org.example.notification.domain.model.NotificationMessagePart;
import org.example.notification.domain.model.NotificationType;
import org.example.notification.exception.NotificationCacheException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RedisNotificationCodec implements RedisHashCodec<RedisNotification> {

    private final ObjectMapper objectMapper;

    @Override
    public RedisNotification read(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }

        return RedisNotification.builder()
                .id(nullable(source.get("id")))
                .type(parseEnum(source.get("type"), NotificationType.class))
                .title(nullable(source.get("title")))
                .message(nullable(source.get("message")))
                .messageParts(parseMessageParts(source.get("message_parts")))
                .link(nullable(source.get("link")))
                .payload(parsePayload(source.get("payload")))
                .createdAt(parseInstant(source.get("created_at")))
                .build();
    }

    @Override
    public Map<String, String> write(RedisNotification notification) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("id", str(notification.getId()));
        map.put("type", notification.getType() == null ? "" : notification.getType().name());
        map.put("title", str(notification.getTitle()));
        map.put("message", str(notification.getMessage()));
        map.put("message_parts", json(notification.getMessageParts()));
        map.put("link", str(notification.getLink()));
        map.put("payload", json(notification.getPayload()));
        map.put("created_at", str(notification.getCreatedAt()));
        return map;
    }

    @Override
    public Map<String, String> writePartial(Map<String, Object> updated) {
        // master 정보는 불변이라 부분 갱신 경로가 없다. 계약 충족용 최소 구현.
        Map<String, String> map = new LinkedHashMap<>();
        updated.forEach((k, v) -> map.put(k, str(v)));
        return map;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String json(Object value) {
        if (value == null) {
            return "";
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new NotificationCacheException("[redis] notification json 직렬화 실패", e);
        }
    }

    private List<NotificationMessagePart> parseMessageParts(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(raw, new TypeReference<List<NotificationMessagePart>>() {});
        } catch (JsonProcessingException e) {
            throw new NotificationCacheException("[redis] message_parts 역직렬화 실패", e);
        }
    }

    private Map<String, Object> parsePayload(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new NotificationCacheException("[redis] payload 역직렬화 실패", e);
        }
    }

    private Instant parseInstant(String raw) {
        return (raw == null || raw.isBlank()) ? null : Instant.parse(raw);
    }

    private <E extends Enum<E>> E parseEnum(String raw, Class<E> enumType) {
        return (raw == null || raw.isBlank()) ? null : Enum.valueOf(enumType, raw);
    }

    private String nullable(String raw) {
        return (raw == null || raw.isBlank()) ? null : raw;
    }
}
