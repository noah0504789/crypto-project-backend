package org.example.chat.chatmessage.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.chat.chatmessage.domain.event.dlq.ChatMessageDlqEventList;
import org.example.chat.chatmessage.domain.event.payload.ChatMessagePayload;
import org.example.chat.chatmessage.domain.event.ChatMessageBroadcastEvent;
import org.example.chat.chatmessage.domain.event.ChatMessageEventList;
import org.example.chat.chatmessage.domain.event.dlq.ChatMessagePersistDlqEvent;
import org.example.chat.chatmessage.domain.event.ChatMessagePersistEvent;
import org.example.chat.chatroom.domain.event.payload.MyChatRoomPayload;
import org.example.chat.chatroom.domain.event.MyChatRoomBadgeEvent;
import org.example.common.time.ServiceZoneUtils;

import java.time.Instant;
import java.time.LocalDateTime;
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
                .createdAt(LocalDateTime.ofInstant(createdAt, ServiceZoneUtils.ZONE_ID))
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
                .createdAt(LocalDateTime.ofInstant(payload.createdAt(), ServiceZoneUtils.ZONE_ID))
                .eventList(new ChatMessageEventList())
                .dlqEventList(new ChatMessageDlqEventList())
                .build();
    }

    public void registerPersistEvents(Set<String> memberIds, String clientMessageId) {
        ChatMessagePayload payload = ChatMessagePayload.fromDomain(this);

        this.eventList
                .addEvent(new ChatMessagePersistEvent(payload, memberIds))
                .addEvent(new ChatMessageBroadcastEvent(payload, memberIds, clientMessageId))
                .addEvent(new MyChatRoomBadgeEvent(
                        MyChatRoomPayload.ofLastMessage(roomId, memberIds, content, this.toInstant())
                ));
    }

    public void registerPersistDlqEvents(String errorMessage) {
        this.dlqEventList.addEvent(
                new ChatMessagePersistDlqEvent(ChatMessagePayload.fromDomain(this), errorMessage)
        );
    }

    public ChatMessageEventList pullEventList() {
        ChatMessageEventList pulledEventList = this.eventList;
        this.eventList = new ChatMessageEventList();

        return pulledEventList;
    }

    public ChatMessageDlqEventList pullDlqEventList() {
        ChatMessageDlqEventList pulledEventList = this.dlqEventList;
        this.dlqEventList = new ChatMessageDlqEventList();

        return pulledEventList;
    }

    public Instant toInstant() {
        return createdAt.atZone(ServiceZoneUtils.ZONE_ID).toInstant();
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
