package org.example.chat.chatroom.adapter.out.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.chat.common.exception.ChatCacheException;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.common.redis.codec.RedisHashCodec;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RedisChatRoomCodec implements RedisHashCodec<RedisChatRoom> {

    private final ObjectMapper objectMapper;

    @Override
    public RedisChatRoom read(Map<String, String> source) {
        if (source == null || source.isEmpty()) return null;

        return RedisChatRoom.builder()
                .id(nullable(source.get("id")))
                .hostId(nullable(source.get("host_id")))
                .title(nullable(source.get("title")))
                .description(nullable(source.get("description")))
                .category(parseEnum(source.get("category"), ChatRoomCategory.class))
                .memberIds(parseStringSet(source.get("member_ids")))
                .msgCnt(parseLongOrDefault(source.get("msg_cnt"), 0L))
                .createdAt(parseInstant(source.get("created_at")))
                .build();
    }

    @Override
    public Map<String, String> write(RedisChatRoom room) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("id", str(room.getId()));
        map.put("host_id", str(room.getHostId()));
        map.put("title", str(room.getTitle()));
        map.put("description", str(room.getDescription()));
        map.put("category", room.getCategory() == null ? "" : room.getCategory().name());
        map.put("member_ids", json(room.getMemberIds()));
        map.put("msg_cnt", room.getMsgCnt() == null ? "0" : room.getMsgCnt()+"");
        map.put("created_at", str(room.getCreatedAt()));
        return map;
    }

    @Override
    public Map<String, String> writePartial(Map<String, Object> updated) {
        Map<String, String> map = new LinkedHashMap<>();

        updated.forEach((k, v) -> {
            switch (k) {
                case "id" -> map.put("id", str(v));
                case "hostId", "host_id" -> map.put("host_id", str(v));
                case "title" -> map.put("title", str(v));
                case "description" -> map.put("description", str(v));
                case "category" -> map.put("category", v == null ? "" : (v instanceof ChatRoomCategory category ? category.name() : String.valueOf(v)));
                case "memberIds", "member_ids" -> map.put("member_ids", json(v));
                case "msgCnt", "msg_cnt" -> map.put("msg_cnt", str(v));
//                case "lastMsgId", "last_msg_id" -> map.put("last_msg_id", str(v));
//                case "lastMsgContent", "last_msg_content" -> map.put("last_msg_content", str(v));
//                case "lastMsgCreatedAt", "last_msg_created_at" -> map.put("last_msg_created_at", str(v));
                case "createdAt", "created_at" -> map.put("created_at", str(v));
                default -> map.put(k, str(v));
            }
        });

        return map;
    }

    private Long parseLongOrDefault(String raw, Long defaultValue) {
        return (raw == null || raw.isBlank()) ? defaultValue : Long.valueOf(raw);
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String json(Object value) {
        if (value == null) return "";

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ChatCacheException("[redis] json 직렬화 실패", e);
        }
    }

    private Set<String> parseStringSet(String raw) {
        if (raw == null || raw.isBlank()) return new LinkedHashSet<>();

        try {
            return objectMapper.readValue(raw, new TypeReference<LinkedHashSet<String>>() {});
        } catch (JsonProcessingException e) {
            throw new ChatCacheException("[redis] member_ids 역직렬화 실패", e);
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