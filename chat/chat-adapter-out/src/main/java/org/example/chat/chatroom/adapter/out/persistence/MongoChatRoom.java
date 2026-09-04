package org.example.chat.chatroom.adapter.out.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.bson.types.ObjectId;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatroom.domain.service.ChatRoomPopularityCalculator;
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
@CompoundIndex(name = "idx_category_popularity", def = "{\"category\": 1, \"popularity\": -1, \"_id\": -1}", partialFilter = "{'deleted': false}")
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
    private Long lastMsgSeq;
    private Instant lastMsgCreatedAt;

    // 인기방 정렬 스코어(denormalized). ChatRoomPopularityCalculator 산식의 반올림 값으로,
    // ChatRoomPopularityScheduler가 주기적으로 재계산해 갱신한다(실시간 아님). 정렬/커서는 이 필드로.
    private Long popularity;

    private boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;

    @PersistenceCreator
    public MongoChatRoom(
            String hostId,
            String title,
            String description,
            ChatRoomCategory category,
            LocalDateTime createdAt
    ) {
        this.hostId = hostId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.memberIds = new HashSet<>(Set.of(hostId));
        this.msgCnt = 0L;
        this.lastMsgSeq = 0L;
        this.popularity = 0L;
        this.createdAt = createdAt;
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
                .lastMsgSeq(domain.getLastMsgSeq())
                .popularity(popularityOf(domain))
                .createdAt(domain.getCreatedAt())
                .build();
    }

    private static Long popularityOf(ChatRoom domain) {
        return Math.round(ChatRoomPopularityCalculator.calculate(domain));
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
                lastMsgSeq,
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
                lastMsgSeq,
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
