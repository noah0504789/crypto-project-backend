package org.example.chat.chatroom.domain.model;

import lombok.*;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatroom.domain.exception.ChatRoomAccessDeniedException;
import org.example.chat.chatroom.domain.exception.ChatRoomHostMismatchException;
import org.example.chat.chatroom.domain.exception.ChatRoomMembershipNotFoundException;
import org.example.chat.chatroom.domain.service.ChatRoomPopularityCalculator;
import org.example.common.time.ServiceTimeConverter;

import java.time.Instant;
import java.time.LocalDateTime;
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
    private Long latestMsgSeq;
    private String lastMsgId;
    private String lastMsgContent;
    private Instant lastMsgCreatedAt;
    private LocalDateTime createdAt;

    public static ChatRoom create(
            String id,
            String hostId,
            String title,
            String description,
            ChatRoomCategory category,
            LocalDateTime createdAt
    ) {
        return ChatRoom.builder()
                .id(id)
                .hostId(hostId)
                .title(title)
                .description(description)
                .category(category)
                .msgCnt(0L)
                .latestMsgSeq(0L)
                .memberIds(new HashSet<>(Set.of(hostId)))
                .createdAt(createdAt)
                .build();
    }

    public static ChatRoom rehydrate(String id, ChatRoomCategory category, LocalDateTime createdAt) {
        return ChatRoom.builder()
                .id(id)
                .msgCnt(0L)
                .latestMsgSeq(0L)
                .category(category)
                .createdAt(createdAt)
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
        return rehydrate(id, hostId, title, description, category, memberIds, msgCnt, msgCnt, createdAt);
    }

    public static ChatRoom rehydrate(
            String id,
            String hostId,
            String title,
            String description,
            ChatRoomCategory category,
            Set<String> memberIds,
            Long msgCnt,
            Long latestMsgSeq,
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
                .latestMsgSeq(latestMsgSeq == null ? defaultSequence(msgCnt) : latestMsgSeq)
                .createdAt(createdAt)
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
        return rehydrateWithLatest(
                id, hostId, title, description, category, memberIds, msgCnt, msgCnt,
                lastMsgId, lastMsgContent, lastMsgCreatedAt, createdAt
        );
    }

    public static ChatRoom rehydrateWithLatest(
            String id,
            String hostId,
            String title,
            String description,
            ChatRoomCategory category,
            Set<String> memberIds,
            Long msgCnt,
            Long latestMsgSeq,
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
                .latestMsgSeq(latestMsgSeq == null ? defaultSequence(msgCnt) : latestMsgSeq)
                .lastMsgId(lastMsgId == null ? "" : lastMsgId)
                .lastMsgContent(lastMsgContent == null ? "" : lastMsgContent)
                .lastMsgCreatedAt(lastMsgCreatedAt)
                .createdAt(createdAt)
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
                id, hostId, title, description, category, memberIds, msgCnt, msgCnt, latest, createdAt
        );
    }

    public static ChatRoom rehydrateWithLatest(
            String id,
            String hostId,
            String title,
            String description,
            ChatRoomCategory category,
            Set<String> memberIds,
            Long msgCnt,
            Long latestMsgSeq,
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
                latestMsgSeq,
                latest == null ? "" : latest.getId(),
                latest == null ? "" : latest.getContent(),
                latest == null ? null : latest.createdAtInstant(),
                createdAt
        );
    }

    public Double popularity() {
        return ChatRoomPopularityCalculator.calculate(this);
    }

    public long lastMsgCreatedAtMs() {
        return lastMsgCreatedAt == null ? 0L : lastMsgCreatedAt.toEpochMilli();
    }

    public Instant createdAtInstant() {
        return ServiceTimeConverter.toInstant(createdAt);
    }

    public void validateWritable(String writerId) {
        if (writerId == null || writerId.isBlank() || memberIds == null || !memberIds.contains(writerId)) {
            throw new ChatRoomMembershipNotFoundException(id, writerId);
        }
    }

    public void validateHost(String myUserId) {
        if (myUserId == null || myUserId.isBlank() || !Objects.equals(hostId, myUserId)) {
            throw new ChatRoomHostMismatchException(id, myUserId);
        }
    }

    public void validateMember(String myUserId) {
        if (myUserId == null || myUserId.isBlank() || memberIds == null || !memberIds.contains(myUserId)) {
            throw new ChatRoomAccessDeniedException(id, myUserId);
        }
    }

    public boolean addMember(String memberId) {
        if (memberIds.contains(memberId)) return false;

        memberIds.add(memberId);

        return true;
    }

    public boolean removeMember(String memberId) {
        if (!memberIds.contains(memberId)) return false;

        memberIds.remove(memberId);

        return true;
    }

    public boolean hasNoMembers() {
        return memberIds == null || memberIds.isEmpty();
    }

    public boolean isLastMember(String memberId) {
        return memberIds.size() == 1 && memberIds.contains(memberId);
    }

    public boolean hasUnread(Long lastReadSeq) {
        if (lastReadSeq == null) lastReadSeq = 0L;
        long latestSeq = latestMsgSeq == null ? (msgCnt == null ? 0L : msgCnt) : latestMsgSeq;

        return lastReadSeq < latestSeq;
    }

    private static long defaultSequence(Long msgCnt) {
        return msgCnt == null ? 0L : msgCnt;
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
