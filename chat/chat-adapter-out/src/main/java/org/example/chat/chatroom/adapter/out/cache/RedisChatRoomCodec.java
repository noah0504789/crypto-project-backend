package org.example.chat.chatroom.adapter.out.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.common.redis.codec.RedisCodecSupport;
import org.example.common.redis.codec.RedisHashCodec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RedisChatRoomCodec implements RedisHashCodec<RedisChatRoom> {

    private final ObjectMapper objectMapper;

    @Override
    public RedisChatRoom read(Map<String, String> source) {
        if (source == null || source.isEmpty()) return null;

        return RedisChatRoom.builder()
                .id(RedisCodecSupport.nullable(source.get("id")))
                .hostId(RedisCodecSupport.nullable(source.get("host_id")))
                .title(RedisCodecSupport.nullable(source.get("title")))
                .description(RedisCodecSupport.nullable(source.get("description")))
                .category(RedisCodecSupport.parseEnum(source.get("category"), ChatRoomCategory.class))
                .memberIds(RedisCodecSupport.fromJson(
                        objectMapper, source.get("member_ids"), new TypeReference<LinkedHashSet<String>>() {}, new LinkedHashSet<>()))
                .msgCnt(RedisCodecSupport.parseLongOrDefault(source.get("msg_cnt"), 0L))
                .lastMsgSeq(RedisCodecSupport.parseLongOrDefault(
                        source.get("last_msg_seq"),
                        RedisCodecSupport.parseLongOrDefault(source.get("msg_cnt"), 0L)
                ))
                .createdAt(RedisCodecSupport.parseInstant(source.get("created_at")))
                .build();
    }

    @Override
    public Map<String, String> write(RedisChatRoom room) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("id", RedisCodecSupport.str(room.getId()));
        map.put("host_id", RedisCodecSupport.str(room.getHostId()));
        map.put("title", RedisCodecSupport.str(room.getTitle()));
        map.put("description", RedisCodecSupport.str(room.getDescription()));
        map.put("category", room.getCategory() == null ? "" : room.getCategory().name());
        map.put("member_ids", RedisCodecSupport.toJson(objectMapper, room.getMemberIds()));
        map.put("msg_cnt", room.getMsgCnt() == null ? "0" : room.getMsgCnt() + "");
        map.put("last_msg_seq", room.getLastMsgSeq() == null ? "0" : room.getLastMsgSeq() + "");
        map.put("created_at", RedisCodecSupport.str(room.getCreatedAt()));
        return map;
    }

    @Override
    public Map<String, String> writePartial(Map<String, Object> updated) {
        Map<String, String> map = new LinkedHashMap<>();

        updated.forEach((k, v) -> {
            switch (k) {
                case "id" -> map.put("id", RedisCodecSupport.str(v));
                case "hostId", "host_id" -> map.put("host_id", RedisCodecSupport.str(v));
                case "title" -> map.put("title", RedisCodecSupport.str(v));
                case "description" -> map.put("description", RedisCodecSupport.str(v));
                case "category" -> map.put("category", v == null ? "" : (v instanceof ChatRoomCategory category ? category.name() : String.valueOf(v)));
                case "memberIds", "member_ids" -> map.put("member_ids", RedisCodecSupport.toJson(objectMapper, v));
                case "msgCnt", "msg_cnt" -> map.put("msg_cnt", RedisCodecSupport.str(v));
                case "lastMsgSeq", "last_msg_seq" ->
                        map.put("last_msg_seq", RedisCodecSupport.str(v));
                case "createdAt", "created_at" -> map.put("created_at", RedisCodecSupport.str(v));
                default -> map.put(k, RedisCodecSupport.str(v));
            }
        });

        return map;
    }
}
