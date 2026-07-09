package org.example.chat.chatroom.adapter.out.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.bson.types.ObjectId;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Document("chat_room")
@CompoundIndex(name = "idx_category_msgCnt", def = "{\"category\": 1, \"msgCnt\": -1, \"_id\": -1}", partialFilter = "{'deleted': false}")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@ToString
public class MongoChatRoom {

    @MongoId
    private ObjectId id;
    private String hostId;

    @Indexed(unique = true, partialFilter = "{'deleted': false}")
    private String title;
    private String description;
    private ChatRoomCategory category;
    private Set<String> memberIds;
    private Long msgCnt;
    private boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;

    @PersistenceCreator
    public MongoChatRoom(String hostId, String title, String description, ChatRoomCategory category) {
        this.hostId = hostId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.memberIds = new HashSet<>(Set.of(hostId));
        this.msgCnt = 0L;
        this.createdAt = LocalDateTime.now();
    }

    public static MongoChatRoom fromDomain(ChatRoom domain) {
        return MongoChatRoom.builder()
                .id(new ObjectId(domain.getId()))
                .hostId(domain.getHostId())
                .title(domain.getTitle())
                .description(domain.getDescription())
                .category(domain.getCategory())
                .memberIds(domain.getMemberIds())
                .msgCnt(domain.getMsgCnt())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    public ChatRoom toDomain() {
        return ChatRoom.rehydrate(
                id.toHexString(),
                hostId,
                title,
                description,
                category,
                memberIds,
                msgCnt,
                createdAt
        );
    }

    public ChatRoom toDomainWithLatest(
            String latestMessageId,
            String latestMessage,
            Instant latestMessageCreatedAt
    ) {
        return ChatRoom.rehydrateWithLatest(
                id.toHexString(),
                hostId,
                title,
                description,
                category,
                memberIds,
                msgCnt,
                latestMessageId == null ? "" : latestMessageId,
                latestMessage == null ? "" : latestMessage,
                latestMessageCreatedAt,
                createdAt
        );
    }

    public ChatRoom toDomainWithNoLatestMessage() {
        return toDomainWithLatest(null, null, null);
    }
}
