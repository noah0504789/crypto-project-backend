package org.example.chat.chatmessage.adapter.out.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.bson.types.ObjectId;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.common.time.ServiceZoneUtils;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import java.time.Instant;

@Document("chat_message")
@CompoundIndex(name = "idx_room_created_id", def = "{\"room_id\": 1, \"created_at\": -1, \"_id\": -1}", partialFilter = "{'deleted': false}")
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@ToString
public class MongoChatMessage {

    @MongoId
    private ObjectId id;
    private ObjectId roomId;
    private String writerId;
    private String content;
    private boolean deleted;
    private Instant createdAt;
    private Instant deletedAt;

    @PersistenceCreator
    public MongoChatMessage(ObjectId roomId, String writerId, String content) {
        this.roomId = roomId;
        this.writerId = writerId;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public static MongoChatMessage fromDomain(ChatMessage domain) {
        return MongoChatMessage.builder()
                .id(new ObjectId(domain.getId()))
                .roomId(new ObjectId(domain.getRoomId()))
                .writerId(domain.getWriterId())
                .content(domain.getContent())
                .createdAt(domain.createdAtInstant())
                .build();
    }

    public ChatMessage toDomain() {
        return ChatMessage.rehydrate(
                id.toHexString(),
                roomId.toHexString(),
                writerId,
                content,
                createdAt
        );
    }
}
