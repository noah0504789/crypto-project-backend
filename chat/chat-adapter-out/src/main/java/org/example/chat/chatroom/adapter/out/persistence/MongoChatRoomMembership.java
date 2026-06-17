package org.example.chat.chatroom.adapter.out.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import org.example.chat.chatroom.domain.service.MyChatRoomScoreCalculator;

@Document("chat_room_membership")
@CompoundIndexes({
        @CompoundIndex(name = "unique_keys", def = "{\"room_id\": 1, \"member_id\": 1}", unique = true),
        @CompoundIndex(name = "my_rooms", def = "{\"member_id\": 1, \"score\": -1, \"_id\": -1}")
})
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@ToString
public class MongoChatRoomMembership {

    @Id
    private String id;
    private ObjectId roomId;
    private String memberId;
    private Long lastMsgReadSeq;
    private Long score;

    public static MongoChatRoomMembership ofUnreadActivity(String roomId, String memberId, Long score) {
        return MongoChatRoomMembership.builder()
                .id(generateId(roomId, memberId))
                .roomId(new ObjectId(roomId))
                .memberId(memberId)
                .score(score)
                .build();
    }

    public static MongoChatRoomMembership ofReadActivity(String roomId, String memberId, Long lastMsgReadSeq, Long score) {
        return MongoChatRoomMembership.builder()
                .id(generateId(roomId, memberId))
                .roomId(new ObjectId(roomId))
                .memberId(memberId)
                .lastMsgReadSeq(lastMsgReadSeq)
                .score(score)
                .build();
    }

    public static String generateId(String roomId, String memberId) {
        return roomId + "|" + memberId;
    }
}
