package org.example.chatmessage.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.chatmessage.adapter.dto.ChatMessageDlqEventList;
import org.example.chatmessage.adapter.dto.ChatMessagePayload;
import org.example.chatmessage.domain.model.event.ChatMessageBroadcastEvent;
import org.example.chatmessage.adapter.dto.ChatMessageEventList;
import org.example.chatmessage.domain.model.event.dlq.ChatMessagePersistDlqEvent;
import org.example.chatmessage.domain.model.event.ChatMessagePersistEvent;
import org.example.chatroom.adapter.dto.MyChatRoomPayload;
import org.example.chatroom.domain.model.event.MyChatRoomBadgeEvent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessage {

    private String id;
    private String roomId;
    private String writerId;
    private String content;
    private LocalDateTime createdAt;
    private ChatMessageEventList eventList;
    private ChatMessageDlqEventList dlqEventList;

    public static ChatMessage ofNewMessage(String id, String roomId, String writerId, String content) {
        return ChatMessage.builder()
                .id(id)
                .roomId(roomId)
                .writerId(writerId)
                .content(content)
                .createdAt(LocalDateTime.now())
                .eventList(new ChatMessageEventList())
                .dlqEventList(new ChatMessageDlqEventList())
                .build();
    }

    public static ChatMessage rehydrate(String id, String roomId, String writerId, String content, Instant createdAt) {
        return ChatMessage.builder()
                .id(id)
                .roomId(roomId)
                .writerId(writerId)
                .content(content)
                .createdAt(LocalDateTime.ofInstant(createdAt, ZoneId.systemDefault()))
                .eventList(new ChatMessageEventList())
                .dlqEventList(new ChatMessageDlqEventList())
                .build();
    }

    public static ChatMessage fromPayload(ChatMessagePayload payload) {
        return ChatMessage.builder()
                .id(payload.id())
                .roomId(payload.roomId())
                .writerId(payload.writerId())
                .content(payload.content())
                .createdAt(LocalDateTime.ofInstant(payload.createdAt(), ZoneId.systemDefault()))
                .eventList(new ChatMessageEventList())
                .dlqEventList(new ChatMessageDlqEventList())
                .build();
    }

    public void persist(Set<String> memberIds, String clientMessageId) {
        this.eventList
                .addEvent(new ChatMessagePersistEvent(ChatMessagePayload.fromDomain(this), memberIds))
                .addEvent(new ChatMessageBroadcastEvent(ChatMessagePayload.fromDomain(this), memberIds, clientMessageId))
                .addEvent(new MyChatRoomBadgeEvent(MyChatRoomPayload.ofLastMessage(roomId, memberIds, content, this.toInstant())));
//                .addEvent(new WebNotificationEvent(this.getClass().getName(), WebNotificationPayload.fromChatMessage(this), writerId))

        this.eventList.publish();
    }

    public void recoverPersist(String errorMessage) {
        this.dlqEventList.addEvent(new ChatMessagePersistDlqEvent(ChatMessagePayload.fromDomain(this), errorMessage));
        this.dlqEventList.publish();
    }

    public Instant toInstant() {
        return createdAt.atZone(ZoneId.systemDefault()).toInstant();
    }

    public long toEpochMillis() {
        return toInstant().toEpochMilli();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (this.getClass() != obj.getClass()) return false;

        ChatMessage that = (ChatMessage) obj;

        return this.getId().equals(that.getId());
    }
}
