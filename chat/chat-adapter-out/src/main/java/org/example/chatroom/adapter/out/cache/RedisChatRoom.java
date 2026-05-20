package org.example.chatroom.adapter.out.cache;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.example.chatroom.domain.model.ChatRoom;
import org.example.chatroom.domain.model.ChatRoomCategory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

@ToString
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RedisChatRoom {

    private String id;

    @JsonProperty("host_id")
    private String hostId;
    private String title;
    private String description;
    private ChatRoomCategory category;

    @JsonProperty("member_ids")
    private Set<String> memberIds;

    @JsonProperty("msg_cnt")
    private Long msgCnt;

    @JsonProperty("created_at")
    private Instant createdAt;

    public static RedisChatRoom fromDomain(ChatRoom domain) {
        return RedisChatRoom.builder()
                .id(domain.getId())
                .hostId(domain.getHostId())
                .title(domain.getTitle())
                .description(domain.getDescription())
                .category(domain.getCategory())
                .memberIds(domain.getMemberIds())
                .msgCnt(domain.getMsgCnt() == null ? 0L : domain.getMsgCnt())
                .createdAt(domain.toInstant())
                .build();
    }

    public LocalDateTime toLocalDateTime() {
        return LocalDateTime.ofInstant(createdAt, ZoneId.systemDefault());
    }
}
