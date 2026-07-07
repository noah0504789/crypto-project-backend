package org.example.chat.chatmessage.adapter.out.persistence;

import org.bson.types.ObjectId;
import org.example.chat.chatmessage.application.exception.ChatMessagePersistException;
import org.example.chat.chatmessage.application.exception.DuplicateChatMessageException;
import org.example.chat.chatmessage.application.event.dlq.ChatMessageDlqEventList;
import org.example.chat.chatmessage.application.event.ChatMessageEventList;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.exception.ChatPersistenceException;
import org.example.chat.exception.InvalidResourceRequestException;
import org.example.chat.exception.TemporaryChatPersistenceException;
import org.example.common.time.ServiceZoneUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MongoChatMessageAdapterTest {

    @Mock
    private MongoChatMessageRepository repository;

    @InjectMocks
    private MongoChatMessageAdapter sut;

    private final ObjectId roomId = new ObjectId("000000000000000000000001");

    private final ObjectId messageId1 = new ObjectId("100000000000000000000001");
    private final ObjectId messageId2 = new ObjectId("100000000000000000000002");
    private final ObjectId messageId3 = new ObjectId("100000000000000000000003");

    private final String ROOM_ID = roomId.toHexString();
    private final String MESSAGE_ID_1 = messageId1.toHexString();
    private final String MESSAGE_ID_2 = messageId2.toHexString();
    private final String MESSAGE_ID_3 = messageId3.toHexString();

    private final String WRITER_ID = "writer-1";
    private final String CONTENT_1 = "첫 번째 메시지";
    private final String CONTENT_2 = "두 번째 메시지";
    private final String CONTENT_3 = "세 번째 메시지";

    private final Instant time1 = Instant.parse("2026-01-01T01:00:00Z"); // KST 10:00
    private final Instant time2 = Instant.parse("2026-01-01T02:00:00Z"); // KST 11:00
    private final Instant time3 = Instant.parse("2026-01-01T03:00:00Z"); // KST 12:00

    private final LocalDateTime domainTime1 = LocalDateTime.ofInstant(time1, ServiceZoneUtils.ZONE_ID);
    private final LocalDateTime domainTime2 = LocalDateTime.ofInstant(time2, ServiceZoneUtils.ZONE_ID);
    private final LocalDateTime domainTime3 = LocalDateTime.ofInstant(time3, ServiceZoneUtils.ZONE_ID);

    @Nested
    @DisplayName("listLatest")
    class ListLatestTest {

        @Test
        @DisplayName("최신 메시지를 createdAt desc, _id desc 정렬로 조회한다")
        void listLatest() {
            // given
            int limit = 2;

            MongoChatMessage latest = mongoMessage(messageId3, CONTENT_3, time3);
            MongoChatMessage second = mongoMessage(messageId2, CONTENT_2, time2);

            given(repository.findByRoomIdAndDeletedFalse(eq(roomId), any(Pageable.class)))
                    .willReturn(List.of(latest, second));

            // when
            List<ChatMessage> result = sut.listLatestMessages(ROOM_ID, limit);

            // then
            assertThat(result)
                    .extracting(ChatMessage::getId)
                    .containsExactly(MESSAGE_ID_3, MESSAGE_ID_2);

            assertThat(result.get(0).getContent()).isEqualTo(CONTENT_3);
            assertThat(result.get(0).getCreatedAt()).isEqualTo(domainTime3);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

            verify(repository).findByRoomIdAndDeletedFalse(eq(roomId), pageableCaptor.capture());

            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageNumber()).isEqualTo(0);
            assertThat(pageable.getPageSize()).isEqualTo(limit);

            Sort sort = pageable.getSort();
            assertThat(sort.getOrderFor("createdAt")).isNotNull();
            assertThat(Objects.requireNonNull(sort.getOrderFor("createdAt")).getDirection()).isEqualTo(Sort.Direction.DESC);
            assertThat(sort.getOrderFor("_id")).isNotNull();
            assertThat(Objects.requireNonNull(sort.getOrderFor("_id")).getDirection()).isEqualTo(Sort.Direction.DESC);
        }

        @Test
        @DisplayName("조회 결과가 없으면 빈 리스트를 반환한다")
        void listLatestEmpty() {
            // given
            given(repository.findByRoomIdAndDeletedFalse(eq(roomId), any(Pageable.class)))
                    .willReturn(List.of());

            // when
            List<ChatMessage> result = sut.listLatestMessages(ROOM_ID, 10);

            // then
            assertThat(result).isEmpty();
            verify(repository).findByRoomIdAndDeletedFalse(eq(roomId), any(Pageable.class));
        }

        @Test
        @DisplayName("잘못된 roomId이면 InvalidResourceRequestException을 던진다")
        void listLatestThrowsInvalidResourceRequestExceptionWhenRoomIdIsInvalid() {
            // when & then
            assertThatThrownBy(() -> sut.listLatestMessages("invalid-room-id", 10))
                    .isInstanceOf(InvalidResourceRequestException.class)
                    .hasMessageContaining("invalid ObjectId")
                    .hasMessageContaining("roomId");

            verify(repository, never()).findByRoomIdAndDeletedFalse(any(), any(Pageable.class));
        }

        @Test
        @DisplayName("조회 중 일시적 저장소 예외가 발생하면 TemporaryChatPersistenceException으로 변환한다")
        void listLatestThrowsTemporaryChatPersistenceExceptionWhenTemporaryFailureOccurs() {
            // given
            TransientDataAccessResourceException exception =
                    new TransientDataAccessResourceException("temporary mongo failure");

            given(repository.findByRoomIdAndDeletedFalse(eq(roomId), any(Pageable.class)))
                    .willThrow(exception);

            // when & then
            assertThatThrownBy(() -> sut.listLatestMessages(ROOM_ID, 10))
                    .isInstanceOf(TemporaryChatPersistenceException.class)
                    .hasMessageContaining("failed to list latest chat messages")
                    .hasCause(exception);

            verify(repository).findByRoomIdAndDeletedFalse(eq(roomId), any(Pageable.class));
        }

        @Test
        @DisplayName("조회 중 일반 예외가 발생하면 ChatPersistenceException으로 변환한다")
        void listLatestThrowsChatPersistenceExceptionWhenUnexpectedFailureOccurs() {
            // given
            RuntimeException exception = new RuntimeException("unexpected mongo failure");

            given(repository.findByRoomIdAndDeletedFalse(eq(roomId), any(Pageable.class)))
                    .willThrow(exception);

            // when & then
            assertThatThrownBy(() -> sut.listLatestMessages(ROOM_ID, 10))
                    .isInstanceOf(ChatPersistenceException.class)
                    .isNotInstanceOf(TemporaryChatPersistenceException.class)
                    .hasMessageContaining("failed to list latest chat messages")
                    .hasCause(exception);

            verify(repository).findByRoomIdAndDeletedFalse(eq(roomId), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("listPrev")
    class ListPrevTest {

        @Test
        @DisplayName("cursor 이전 메시지를 repository.listPrev로 조회한다")
        void listPrev() {
            // given
            long lastCreatedAtMillis = time3.toEpochMilli();
            int limit = 2;

            MongoChatMessage message2 = mongoMessage(messageId2, CONTENT_2, time2);
            MongoChatMessage message1 = mongoMessage(messageId1, CONTENT_1, time1);

            given(repository.listMessagesBefore(roomId, messageId3, time3, limit))
                    .willReturn(List.of(message2, message1));

            // when
            List<ChatMessage> result = sut.listMessagesBefore(
                    ROOM_ID,
                    MESSAGE_ID_3,
                    lastCreatedAtMillis,
                    limit
            );

            // then
            assertThat(result)
                    .extracting(ChatMessage::getId)
                    .containsExactly(MESSAGE_ID_2, MESSAGE_ID_1);

            assertThat(result.get(0).getCreatedAt()).isEqualTo(domainTime2);

            verify(repository).listMessagesBefore(roomId, messageId3, time3, limit);
        }

        @Test
        @DisplayName("cursor 이전 메시지가 없으면 빈 리스트를 반환한다")
        void listPrevEmpty() {
            // given
            given(repository.listMessagesBefore(roomId, messageId1, time1, 10))
                    .willReturn(List.of());

            // when
            List<ChatMessage> result = sut.listMessagesBefore(
                    ROOM_ID,
                    MESSAGE_ID_1,
                    time1.toEpochMilli(),
                    10
            );

            // then
            assertThat(result).isEmpty();
            verify(repository).listMessagesBefore(roomId, messageId1, time1, 10);
        }

        @Test
        @DisplayName("잘못된 roomId이면 InvalidResourceRequestException을 던진다")
        void listPrevThrowsInvalidResourceRequestExceptionWhenRoomIdIsInvalid() {
            // when & then
            assertThatThrownBy(() -> sut.listMessagesBefore(
                    "invalid-room-id",
                    MESSAGE_ID_3,
                    time3.toEpochMilli(),
                    10
            ))
                    .isInstanceOf(InvalidResourceRequestException.class)
                    .hasMessageContaining("invalid ObjectId")
                    .hasMessageContaining("roomId");

            verify(repository, never()).listMessagesBefore(any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("잘못된 lastId이면 InvalidResourceRequestException을 던진다")
        void listPrevThrowsInvalidResourceRequestExceptionWhenLastIdIsInvalid() {
            // when & then
            assertThatThrownBy(() -> sut.listMessagesBefore(
                    ROOM_ID,
                    "invalid-message-id",
                    time3.toEpochMilli(),
                    10
            ))
                    .isInstanceOf(InvalidResourceRequestException.class)
                    .hasMessageContaining("invalid ObjectId")
                    .hasMessageContaining("lastMsgId");

            verify(repository, never()).listMessagesBefore(any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("cursor 이전 메시지 조회 중 일시적 저장소 예외가 발생하면 TemporaryChatPersistenceException으로 변환한다")
        void listPrevThrowsTemporaryChatPersistenceExceptionWhenTemporaryFailureOccurs() {
            // given
            TransientDataAccessResourceException exception =
                    new TransientDataAccessResourceException("temporary mongo failure");

            given(repository.listMessagesBefore(roomId, messageId3, time3, 10))
                    .willThrow(exception);

            // when & then
            assertThatThrownBy(() -> sut.listMessagesBefore(
                    ROOM_ID,
                    MESSAGE_ID_3,
                    time3.toEpochMilli(),
                    10
            ))
                    .isInstanceOf(TemporaryChatPersistenceException.class)
                    .hasMessageContaining("failed to list previous chat messages")
                    .hasCause(exception);

            verify(repository).listMessagesBefore(roomId, messageId3, time3, 10);
        }
    }

    @Nested
    @DisplayName("save")
    class SaveTest {

        @Test
        @DisplayName("ChatMessage를 MongoChatMessage로 변환하여 저장하고 원본 도메인을 반환한다")
        void save() {
            // given
            ChatMessage domain = chatMessage(MESSAGE_ID_1, domainTime1);

            // when
            ChatMessage result = sut.save(domain);

            // then
            assertThat(result).isSameAs(domain);

            ArgumentCaptor<MongoChatMessage> captor = ArgumentCaptor.forClass(MongoChatMessage.class);
            verify(repository).save(captor.capture());

            MongoChatMessage saved = captor.getValue();

            assertThat(saved.getId()).isEqualTo(messageId1);
            assertThat(saved.getRoomId()).isEqualTo(roomId);
            assertThat(saved.getWriterId()).isEqualTo(WRITER_ID);
            assertThat(saved.getContent()).isEqualTo(CONTENT_1);
            assertThat(saved.getCreatedAt()).isEqualTo(time1);
            assertThat(saved.isDeleted()).isFalse();
        }

        @Test
        @DisplayName("저장 중 DuplicateKeyException이 발생하면 DuplicateChatMessageException으로 변환한다")
        void saveThrowsDuplicateChatMessageExceptionWhenDuplicateKeyOccurs() {
            // given
            ChatMessage domain = chatMessage(MESSAGE_ID_1, domainTime1);

            DuplicateKeyException exception = new DuplicateKeyException("duplicate message");

            given(repository.save(any(MongoChatMessage.class)))
                    .willThrow(exception);

            // when & then
            assertThatThrownBy(() -> sut.save(domain))
                    .isInstanceOf(DuplicateChatMessageException.class)
                    .hasMessageContaining("failed to save chat message")
                    .hasMessageContaining(MESSAGE_ID_1)
                    .hasCause(exception);

            verify(repository).save(any(MongoChatMessage.class));
        }

        @Test
        @DisplayName("저장 중 일시적 저장소 예외가 발생하면 TemporaryChatPersistenceException으로 변환한다")
        void saveThrowsTemporaryChatPersistenceExceptionWhenTemporaryFailureOccurs() {
            // given
            ChatMessage domain = chatMessage(MESSAGE_ID_1, domainTime1);

            TransientDataAccessResourceException exception =
                    new TransientDataAccessResourceException("temporary mongo failure");

            given(repository.save(any(MongoChatMessage.class)))
                    .willThrow(exception);

            // when & then
            assertThatThrownBy(() -> sut.save(domain))
                    .isInstanceOf(TemporaryChatPersistenceException.class)
                    .hasMessageContaining("failed to save chat message")
                    .hasMessageContaining(MESSAGE_ID_1)
                    .hasCause(exception);

            verify(repository).save(any(MongoChatMessage.class));
        }

        @Test
        @DisplayName("저장 중 일반 예외가 발생하면 ChatMessagePersistException으로 변환한다")
        void saveThrowsChatMessagePersistExceptionWhenUnexpectedFailureOccurs() {
            // given
            ChatMessage domain = chatMessage(MESSAGE_ID_1, domainTime1);

            RuntimeException exception = new RuntimeException("unexpected mongo failure");

            given(repository.save(any(MongoChatMessage.class)))
                    .willThrow(exception);

            // when & then
            assertThatThrownBy(() -> sut.save(domain))
                    .isInstanceOf(ChatMessagePersistException.class)
                    .hasMessageContaining("failed to save chat message")
                    .hasMessageContaining(MESSAGE_ID_1)
                    .hasCause(exception);

            verify(repository).save(any(MongoChatMessage.class));
        }
    }

    @Nested
    @DisplayName("hardDelete")
    class HardDeleteTest {

        @Test
        @DisplayName("repository.hardDelete 결과가 true면 true를 반환한다")
        void hardDeleteSuccess() {
            // given
            given(repository.hardDeleteById(messageId1)).willReturn(true);

            // when
            boolean result = sut.hardDeleteById(MESSAGE_ID_1);

            // then
            assertThat(result).isTrue();
            verify(repository).hardDeleteById(messageId1);
        }

        @Test
        @DisplayName("repository.hardDelete 결과가 false면 false를 반환한다")
        void hardDeleteNotFound() {
            // given
            given(repository.hardDeleteById(messageId1)).willReturn(false);

            // when
            boolean result = sut.hardDeleteById(MESSAGE_ID_1);

            // then
            assertThat(result).isFalse();
            verify(repository).hardDeleteById(messageId1);
        }

        @Test
        @DisplayName("잘못된 messageId이면 InvalidResourceRequestException을 던진다")
        void hardDeleteThrowsInvalidResourceRequestExceptionWhenMessageIdIsInvalid() {
            // when & then
            assertThatThrownBy(() -> sut.hardDeleteById("invalid-message-id"))
                    .isInstanceOf(InvalidResourceRequestException.class)
                    .hasMessageContaining("invalid ObjectId")
                    .hasMessageContaining("messageId");

            verify(repository, never()).hardDeleteById(any());
        }

        @Test
        @DisplayName("hardDelete 중 일시적 저장소 예외가 발생하면 TemporaryChatPersistenceException으로 변환한다")
        void hardDeleteThrowsTemporaryChatPersistenceExceptionWhenTemporaryFailureOccurs() {
            // given
            TransientDataAccessResourceException exception =
                    new TransientDataAccessResourceException("temporary mongo failure");

            given(repository.hardDeleteById(messageId1))
                    .willThrow(exception);

            // when & then
            assertThatThrownBy(() -> sut.hardDeleteById(MESSAGE_ID_1))
                    .isInstanceOf(TemporaryChatPersistenceException.class)
                    .hasMessageContaining("failed to hard delete chat message")
                    .hasCause(exception);

            verify(repository).hardDeleteById(messageId1);
        }

        @Test
        @DisplayName("hardDelete 중 일반 예외가 발생하면 ChatPersistenceException으로 변환한다")
        void hardDeleteThrowsChatPersistenceExceptionWhenUnexpectedFailureOccurs() {
            // given
            RuntimeException exception = new RuntimeException("unexpected mongo failure");

            given(repository.hardDeleteById(messageId1))
                    .willThrow(exception);

            // when & then
            assertThatThrownBy(() -> sut.hardDeleteById(MESSAGE_ID_1))
                    .isInstanceOf(ChatPersistenceException.class)
                    .isNotInstanceOf(TemporaryChatPersistenceException.class)
                    .hasMessageContaining("failed to hard delete chat message")
                    .hasCause(exception);

            verify(repository).hardDeleteById(messageId1);
        }
    }

    @Nested
    @DisplayName("findLatestExcluding")
    class FindLatestExcludingTest {

        @Test
        @DisplayName("지정 메시지를 제외한 최신 메시지를 도메인으로 변환해 반환한다")
        void findLatestExcluding() {
            // given
            MongoChatMessage latest = mongoMessage(messageId2, CONTENT_2, time2);

            given(repository.findLatestMessageExcluding(ROOM_ID, MESSAGE_ID_1))
                    .willReturn(Optional.of(latest));

            // when
            Optional<ChatMessage> result = sut.findLatestMessageExcluding(ROOM_ID, MESSAGE_ID_1);

            // then
            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getId()).isEqualTo(MESSAGE_ID_2);
            assertThat(result.orElseThrow().getContent()).isEqualTo(CONTENT_2);
            assertThat(result.orElseThrow().getCreatedAt()).isEqualTo(domainTime2);

            verify(repository).findLatestMessageExcluding(ROOM_ID, MESSAGE_ID_1);
        }

        @Test
        @DisplayName("조회 결과가 없으면 Optional.empty를 반환한다")
        void findLatestExcludingEmpty() {
            // given
            given(repository.findLatestMessageExcluding(ROOM_ID, MESSAGE_ID_1))
                    .willReturn(Optional.empty());

            // when
            Optional<ChatMessage> result = sut.findLatestMessageExcluding(ROOM_ID, MESSAGE_ID_1);

            // then
            assertThat(result).isEmpty();
            verify(repository).findLatestMessageExcluding(ROOM_ID, MESSAGE_ID_1);
        }

        @Test
        @DisplayName("findLatestExcluding 중 일시적 저장소 예외가 발생하면 TemporaryChatPersistenceException으로 변환한다")
        void findLatestExcludingThrowsTemporaryChatPersistenceExceptionWhenTemporaryFailureOccurs() {
            // given
            TransientDataAccessResourceException exception =
                    new TransientDataAccessResourceException("temporary mongo failure");

            given(repository.findLatestMessageExcluding(ROOM_ID, MESSAGE_ID_1))
                    .willThrow(exception);

            // when & then
            assertThatThrownBy(() -> sut.findLatestMessageExcluding(ROOM_ID, MESSAGE_ID_1))
                    .isInstanceOf(TemporaryChatPersistenceException.class)
                    .hasMessageContaining("failed to find latest excluding chat message")
                    .hasCause(exception);

            verify(repository).findLatestMessageExcluding(ROOM_ID, MESSAGE_ID_1);
        }

        @Test
        @DisplayName("findLatestExcluding 중 일반 예외가 발생하면 ChatPersistenceException으로 변환한다")
        void findLatestExcludingThrowsChatPersistenceExceptionWhenUnexpectedFailureOccurs() {
            // given
            RuntimeException exception = new RuntimeException("unexpected mongo failure");

            given(repository.findLatestMessageExcluding(ROOM_ID, MESSAGE_ID_1))
                    .willThrow(exception);

            // when & then
            assertThatThrownBy(() -> sut.findLatestMessageExcluding(ROOM_ID, MESSAGE_ID_1))
                    .isInstanceOf(ChatPersistenceException.class)
                    .isNotInstanceOf(TemporaryChatPersistenceException.class)
                    .hasMessageContaining("failed to find latest excluding chat message")
                    .hasCause(exception);

            verify(repository).findLatestMessageExcluding(ROOM_ID, MESSAGE_ID_1);
        }
    }

    private MongoChatMessage mongoMessage(ObjectId messageId, String content, Instant createdAt) {
        return MongoChatMessage.builder()
                .id(messageId)
                .roomId(roomId)
                .writerId(WRITER_ID)
                .content(content)
                .createdAt(createdAt)
                .deleted(false)
                .deletedAt(null)
                .build();
    }

    private ChatMessage chatMessage(String messageId, LocalDateTime createdAt) {
        return ChatMessage.builder()
                .id(messageId)
                .roomId(ROOM_ID)
                .writerId(WRITER_ID)
                .content("첫 번째 메시지")
                .createdAt(createdAt)
                .eventList(new ChatMessageEventList())
                .dlqEventList(new ChatMessageDlqEventList())
                .build();
    }
}