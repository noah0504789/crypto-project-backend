package org.example.chat.chatroom.domain.model;

import lombok.*;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatroom.domain.event.*;
import org.example.chat.chatroom.domain.event.dlq.*;
import org.example.chat.chatroom.domain.event.payload.ChatRoomPayload;
import org.example.chat.chatroom.domain.event.payload.ChatRoomUpdatedPayload;
import org.example.chat.chatroom.domain.exception.ChatRoomMembershipNotFoundException;
import org.example.common.time.ServiceZoneUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom {

    private String id;
    private String hostId;
    private String title;
    private String description;
    private ChatRoomCategory category;
    private Set<String> memberIds;
    private Long msgCnt;
    private String lastMsgId;
    private String lastMsgContent;
    private Instant lastMsgCreatedAt;
    private LocalDateTime createdAt;
    private ChatRoomEventList eventList;
    private ChatRoomDlqEventList dlqEventList;

    public static ChatRoom ofId(String id) {
        return ChatRoom.builder()
                .id(id)
                .msgCnt(0L)
                .createdAt(LocalDateTime.now())
                .eventList(new ChatRoomEventList())
                .dlqEventList(new ChatRoomDlqEventList())
                .build();
    }

    public static ChatRoom ofIdAndCategory(String id, ChatRoomCategory category) {
        return ChatRoom.builder()
                .id(id)
                .msgCnt(0L)
                .category(category)
                .createdAt(LocalDateTime.now())
                .eventList(new ChatRoomEventList())
                .dlqEventList(new ChatRoomDlqEventList())
                .build();
    }

    public static ChatRoom ofNewRoom(String id, String hostId, String title, String description, ChatRoomCategory category) {
        return ChatRoom.builder()
                .id(id)
                .hostId(hostId)
                .title(title)
                .description(description)
                .category(category)
                .msgCnt(0L)
                .memberIds(new HashSet<>(Set.of(hostId)))
                .createdAt(LocalDateTime.now())
                .eventList(new ChatRoomEventList())
                .dlqEventList(new ChatRoomDlqEventList())
                .build();
    }

    public static ChatRoom fromPayload(ChatRoomPayload payload) {
        return ChatRoom.builder()
                .id(payload.id())
                .hostId(payload.hostId())
                .title(payload.title())
                .description(payload.description())
                .category(payload.category())
                .msgCnt(0L)
                .memberIds(payload.memberIds() == null ? new HashSet<>() : new HashSet<>(payload.memberIds()))
                .createdAt(payload.toLocalDateTime())
                .eventList(new ChatRoomEventList())
                .dlqEventList(new ChatRoomDlqEventList())
                .build();
    }

    public static ChatRoom rehydrate(
            String id,
            String hostId,
            String title,
            String description,
            ChatRoomCategory category,
            Set<String> memberIds,
            Long msgCnt,
            LocalDateTime createdAt
    ) {
        return ChatRoom.builder()
                .id(id)
                .hostId(hostId)
                .title(title)
                .description(description)
                .category(category)
                .memberIds(memberIds == null ? new HashSet<>() : new HashSet<>(memberIds))
                .msgCnt(msgCnt)
                .createdAt(createdAt)
                .eventList(new ChatRoomEventList())
                .dlqEventList(new ChatRoomDlqEventList())
                .build();
    }

    public static ChatRoom rehydrateWithLatest(
            String id,
            String hostId,
            String title,
            String description,
            ChatRoomCategory category,
            Set<String> memberIds,
            Long msgCnt,
            String lastMsgId,
            String lastMsgContent,
            Instant lastMsgCreatedAt,
            LocalDateTime createdAt
    ) {
        return ChatRoom.builder()
                .id(id)
                .hostId(hostId)
                .title(title)
                .description(description)
                .category(category)
                .memberIds(memberIds == null ? new HashSet<>() : new HashSet<>(memberIds))
                .msgCnt(msgCnt)
                .lastMsgId(lastMsgId == null ? "" : lastMsgId)
                .lastMsgContent(lastMsgContent == null ? "" : lastMsgContent)
                .lastMsgCreatedAt(lastMsgCreatedAt)
                .createdAt(createdAt)
                .eventList(new ChatRoomEventList())
                .dlqEventList(new ChatRoomDlqEventList())
                .build();
    }

    public static ChatRoom rehydrateWithLatest(
            String id,
            String hostId,
            String title,
            String description,
            ChatRoomCategory category,
            Set<String> memberIds,
            Long msgCnt,
            ChatMessage latest,
            LocalDateTime createdAt
    ) {
        return rehydrateWithLatest(
                id,
                hostId,
                title,
                description,
                category,
                memberIds,
                msgCnt,
                latest == null ? "" : latest.getId(),
                latest == null ? "" : latest.getContent(),
                latest == null ? null : latest.toInstant(),
                createdAt
        );
    }

    public void persist() {
        eventList().addEvent(new ChatRoomPersistedEvent(ChatRoomPayload.fromDomain(this)));
    }

    public void update(ChatRoomUpdatedPayload updated) {
        eventList().addEvent(new ChatRoomUpdatedEvent(id, updated));
    }

    public void delete() {
        eventList().addEvent(new ChatRoomDeletedEvent(id, category));
    }

    public void active(String memberId, Long lastMsgSeq, Long lastMsgMs) {
        eventList().addEvent(new ChatRoomActiveEvent(id, memberId, lastMsgSeq, lastMsgMs));
    }

    public void cacheSave() {
        eventList().addEvent(new ChatRoomCacheSaveEvent(id));
    }

    public void cacheUpdate(String oldTitle) {
        eventList().addEvent(new ChatRoomCacheUpdateEvent(id, oldTitle));
    }

    public void cacheDelete() {
        eventList().addEvent(new ChatRoomCacheDeleteEvent(id, category, title, memberIds == null ? Set.of() : memberIds));
    }

    public void cacheActivityInvalidate(String memberId) {
        eventList().addEvent(new ChatRoomCacheActivityInvalidateEvent(id, memberId));
    }

    public void cacheInfoInvalidate() {
        eventList().addEvent(new ChatRoomCacheInfoInvalidateEvent(id));
    }

    public void recoverPersist(String errorMessage) {
        dlqEventList().addEvent(new ChatRoomPersistedDlqEvent(ChatRoomPayload.fromDomain(this), errorMessage));
    }

    public void recoverUpdate(ChatRoomUpdatedEvent event, String errorMessage) {
        dlqEventList().addEvent(new ChatRoomUpdatedDlqEvent(id, event.getUpdated(), errorMessage));
    }

    public void recoverDelete(String errorMessage) {
        dlqEventList().addEvent(new ChatRoomDeletedDlqEvent(id, category, errorMessage));
    }

    public void recoverJoin(ChatRoomJoinedEvent event, String errorMessage) {
        dlqEventList().addEvent(new ChatRoomJoinedDlqEvent(id, event.getMemberId(), errorMessage));
    }

    public void recoverLeave(ChatRoomLeavedEvent event, String errorMessage) {
        dlqEventList().addEvent(new ChatRoomLeavedDlqEvent(id, event.getMemberId(), errorMessage));
    }

    public void recoverActive(ChatRoomActiveEvent event, String errorMessage) {
        dlqEventList().addEvent(new ChatRoomActiveDlqEvent(id, event.getMemberId(), event.getLastMsgSeq(), event.getLastMsgMs(), errorMessage));
    }

    public void recoverCacheSave(String errorMessage) {
        dlqEventList().addEvent(new ChatRoomCacheSaveDlqEvent(id, errorMessage));
    }

    public void recoverCacheUpdate(ChatRoomCacheUpdateEvent event, String errorMessage) {
        dlqEventList().addEvent(new ChatRoomCacheUpdateDlqEvent(id, event.getOldTitle(), errorMessage));
    }

    public void recoverCacheDelete(ChatRoomCacheDeleteEvent event, String errorMessage) {
        dlqEventList().addEvent(new ChatRoomCacheDeleteDlqEvent(id, event.getCategory(), event.getTitle(), event.getMemberids(), errorMessage));
    }

    public void recoverCacheInvalidateActivity(ChatRoomCacheActivityInvalidateEvent event, String errorMessage) {
        dlqEventList().addEvent(new ChatRoomCacheActivityInvalidateDlqEvent(id, event.getMemberId(), errorMessage));
    }

    public void recoverCacheInvalidateInfo(ChatRoomCacheInfoInvalidateEvent event, String errorMessage) {
        dlqEventList().addEvent(new ChatRoomCacheInfoInvalidateDlqEvent(event.getId(), errorMessage));
    }

    public boolean addMember(String memberId) {
        if (memberIds.contains(memberId)) return false;

        memberIds.add(memberId);
        eventList().addEvent(new ChatRoomJoinedEvent(id, memberId));

        return true;
    }

    public boolean removeMember(String memberId) {
        if (!memberIds.contains(memberId)) return false;

        memberIds.remove(memberId);
        eventList().addEvent(new ChatRoomLeavedEvent(id, memberId));

        return true;
    }

    public ChatRoomEventList pullEventList() {
        ChatRoomEventList pulledEventList = eventList();
        this.eventList = new ChatRoomEventList();

        return pulledEventList;
    }

    public ChatRoomDlqEventList pullDlqEventList() {
        ChatRoomDlqEventList pulledEventList = dlqEventList();
        this.dlqEventList = new ChatRoomDlqEventList();

        return pulledEventList;
    }

    public Double getPopularity() {
        return msgCnt == null ? 0 : msgCnt.doubleValue(); // TODO: spec 정의 및 주입받기
    }

    public boolean hasUnread(Long lastReadSeq) {
        if (lastReadSeq == null) lastReadSeq = 0L;
        if (msgCnt == null) return false;

        return lastReadSeq < msgCnt;
    }

    public long getLastMsgCreatedAtMs() {
        return lastMsgCreatedAt == null ? 0L : lastMsgCreatedAt.toEpochMilli();
    }

    public boolean isLastMember(String memberId) {
        return memberIds.size() == 1 && memberIds.contains(memberId);
    }

    public boolean hasNoMembers() {
        return memberIds == null || memberIds.isEmpty();
    }

    public Instant toInstant() {
        return createdAt.atZone(ServiceZoneUtils.ZONE_ID).toInstant();
    }

    public void validateWritable(String writerId) {
        if (writerId == null || writerId.isBlank() || memberIds == null || !memberIds.contains(writerId)) {
            throw new ChatRoomMembershipNotFoundException(id, writerId);
        }
    }

    private ChatRoomEventList eventList() {
        if (this.eventList == null) {
            this.eventList = new ChatRoomEventList();
        }

        return this.eventList;
    }

    private ChatRoomDlqEventList dlqEventList() {
        if (this.dlqEventList == null) {
            this.dlqEventList = new ChatRoomDlqEventList();
        }

        return this.dlqEventList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ChatRoom that = (ChatRoom) o;

        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
