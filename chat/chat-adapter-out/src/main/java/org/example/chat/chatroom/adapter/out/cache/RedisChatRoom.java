package org.example.chat.chatroom.adapter.out.cache;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.common.time.ServiceTimeConverter;

import java.time.Instant;
import java.time.LocalDateTime;
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
                .createdAt(domain.createdAtInstant())
                .build();
    }

    public LocalDateTime toLocalDateTime() {
        return ServiceTimeConverter.toLocalDateTime(createdAt);
    }
}
