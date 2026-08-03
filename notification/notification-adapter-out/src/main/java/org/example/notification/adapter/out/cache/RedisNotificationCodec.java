package org.example.notification.adapter.out.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.common.redis.codec.RedisCodecSupport;
import org.example.common.redis.codec.RedisHashCodec;
import org.example.notification.domain.model.NotificationMessagePart;
import org.example.notification.domain.model.NotificationType;
import org.springframework.stereotype.Component;

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
                .id(RedisCodecSupport.nullable(source.get("id")))
                .type(RedisCodecSupport.parseEnum(source.get("type"), NotificationType.class))
                .title(RedisCodecSupport.nullable(source.get("title")))
                .message(RedisCodecSupport.nullable(source.get("message")))
                .messageParts(RedisCodecSupport.fromJson(
                        objectMapper, source.get("message_parts"), new TypeReference<List<NotificationMessagePart>>() {}, List.of()))
                .link(RedisCodecSupport.nullable(source.get("link")))
                .payload(RedisCodecSupport.fromJson(
                        objectMapper, source.get("payload"), new TypeReference<LinkedHashMap<String, Object>>() {}, new LinkedHashMap<>()))
                .createdAt(RedisCodecSupport.parseInstant(source.get("created_at")))
                .build();
    }

    @Override
    public Map<String, String> write(RedisNotification notification) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("id", RedisCodecSupport.str(notification.getId()));
        map.put("type", notification.getType() == null ? "" : notification.getType().name());
        map.put("title", RedisCodecSupport.str(notification.getTitle()));
        map.put("message", RedisCodecSupport.str(notification.getMessage()));
        map.put("message_parts", RedisCodecSupport.toJson(objectMapper, notification.getMessageParts()));
        map.put("link", RedisCodecSupport.str(notification.getLink()));
        map.put("payload", RedisCodecSupport.toJson(objectMapper, notification.getPayload()));
        map.put("created_at", RedisCodecSupport.str(notification.getCreatedAt()));
        return map;
    }

    @Override
    public Map<String, String> writePartial(Map<String, Object> updated) {
        // 알림 정보는 불변이라 부분 갱신 경로가 없다. 계약 충족용 최소 구현.
        Map<String, String> map = new LinkedHashMap<>();
        updated.forEach((k, v) -> map.put(k, RedisCodecSupport.str(v)));
        return map;
    }
}
