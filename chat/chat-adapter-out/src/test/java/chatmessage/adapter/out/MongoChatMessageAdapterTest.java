package chatmessage.adapter.out;

import org.bson.types.ObjectId;
import org.example.chatmessage.domain.event.dlq.ChatMessageDlqEventList;
import org.example.chatmessage.domain.event.ChatMessageEventList;
import org.example.chatmessage.adapter.out.persistence.MongoChatMessage;
import org.example.chatmessage.adapter.out.persistence.MongoChatMessageAdapter;
import org.example.chatmessage.adapter.out.persistence.MongoChatMessageRepository;
import org.example.chatmessage.domain.model.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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

    private final LocalDateTime domainTime1 = LocalDateTime.ofInstant(time1, ZoneId.systemDefault());
    private final LocalDateTime domainTime2 = LocalDateTime.ofInstant(time2, ZoneId.systemDefault());
    private final LocalDateTime domainTime3 = LocalDateTime.ofInstant(time3, ZoneId.systemDefault());

    @Nested
    @DisplayName("listLatest")
    class ListLatestTest {

        @Test
        @DisplayName("최신 메시지를 createdAt desc, _id desc 정렬로 조회한다")
        void listLatest() {
            // given
            int limit = 2;

            MongoChatMessage latest = mongoMessage(messageId3, CONTENT_3, time3, false);
            MongoChatMessage second = mongoMessage(messageId2, CONTENT_2, time2, false);

            given(repository.findByRoomIdAndDeletedFalse(eq(roomId), any(Pageable.class)))
                    .willReturn(List.of(latest, second));

            // when
            List<ChatMessage> result = sut.listLatest(ROOM_ID, limit);

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
            assertThat(sort.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
            assertThat(sort.getOrderFor("_id")).isNotNull();
            assertThat(sort.getOrderFor("_id").getDirection()).isEqualTo(Sort.Direction.DESC);
        }

        @Test
        @DisplayName("조회 결과가 없으면 빈 리스트를 반환한다")
        void listLatestEmpty() {
            // given
            given(repository.findByRoomIdAndDeletedFalse(eq(roomId), any(Pageable.class)))
                    .willReturn(List.of());

            // when
            List<ChatMessage> result = sut.listLatest(ROOM_ID, 10);

            // then
            assertThat(result).isEmpty();
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

            MongoChatMessage message2 = mongoMessage(messageId2, CONTENT_2, time2, false);
            MongoChatMessage message1 = mongoMessage(messageId1, CONTENT_1, time1, false);

            given(repository.listPrev(roomId, messageId3, time3, limit))
                    .willReturn(List.of(message2, message1));

            // when
            List<ChatMessage> result = sut.listPrev(
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

            verify(repository).listPrev(roomId, messageId3, time3, limit);
        }

        @Test
        @DisplayName("cursor 이전 메시지가 없으면 빈 리스트를 반환한다")
        void listPrevEmpty() {
            // given
            given(repository.listPrev(roomId, messageId1, time1, 10))
                    .willReturn(List.of());

            // when
            List<ChatMessage> result = sut.listPrev(
                    ROOM_ID,
                    MESSAGE_ID_1,
                    time1.toEpochMilli(),
                    10
            );

            // then
            assertThat(result).isEmpty();
            verify(repository).listPrev(roomId, messageId1, time1, 10);
        }
    }

    @Nested
    @DisplayName("save")
    class SaveTest {

        @Test
        @DisplayName("ChatMessage를 MongoChatMessage로 변환하여 저장하고 원본 도메인을 반환한다")
        void save() {
            // given
            ChatMessage domain = chatMessage(MESSAGE_ID_1, CONTENT_1, domainTime1);

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
    }

    @Nested
    @DisplayName("hardDelete")
    class HardDeleteTest {

        @Test
        @DisplayName("repository.hardDelete 결과가 true면 true를 반환한다")
        void hardDeleteSuccess() {
            // given
            given(repository.hardDelete(messageId1)).willReturn(true);

            // when
            boolean result = sut.hardDelete(MESSAGE_ID_1);

            // then
            assertThat(result).isTrue();
            verify(repository).hardDelete(messageId1);
        }

        @Test
        @DisplayName("repository.hardDelete 결과가 false면 false를 반환한다")
        void hardDeleteNotFound() {
            // given
            given(repository.hardDelete(messageId1)).willReturn(false);

            // when
            boolean result = sut.hardDelete(MESSAGE_ID_1);

            // then
            assertThat(result).isFalse();
            verify(repository).hardDelete(messageId1);
        }
    }

    @Nested
    @DisplayName("findLatestExcluding")
    class FindLatestExcludingTest {

        @Test
        @DisplayName("지정 메시지를 제외한 최신 메시지를 도메인으로 변환해 반환한다")
        void findLatestExcluding() {
            // given
            MongoChatMessage latest = mongoMessage(messageId2, CONTENT_2, time2, false);

            given(repository.findLatestExcluding(ROOM_ID, MESSAGE_ID_1))
                    .willReturn(Optional.of(latest));

            // when
            Optional<ChatMessage> result = sut.findLatestExcluding(ROOM_ID, MESSAGE_ID_1);

            // then
            assertThat(result).isPresent();
            assertThat(result.orElseThrow().getId()).isEqualTo(MESSAGE_ID_2);
            assertThat(result.orElseThrow().getContent()).isEqualTo(CONTENT_2);
            assertThat(result.orElseThrow().getCreatedAt()).isEqualTo(domainTime2);

            verify(repository).findLatestExcluding(ROOM_ID, MESSAGE_ID_1);
        }

        @Test
        @DisplayName("조회 결과가 없으면 Optional.empty를 반환한다")
        void findLatestExcludingEmpty() {
            // given
            given(repository.findLatestExcluding(ROOM_ID, MESSAGE_ID_1))
                    .willReturn(Optional.empty());

            // when
            Optional<ChatMessage> result = sut.findLatestExcluding(ROOM_ID, MESSAGE_ID_1);

            // then
            assertThat(result).isEmpty();
            verify(repository).findLatestExcluding(ROOM_ID, MESSAGE_ID_1);
        }
    }

    private MongoChatMessage mongoMessage(
            ObjectId messageId,
            String content,
            Instant createdAt,
            boolean deleted
    ) {
        return MongoChatMessage.builder()
                .id(messageId)
                .roomId(roomId)
                .writerId(WRITER_ID)
                .content(content)
                .createdAt(createdAt)
                .deleted(deleted)
                .deletedAt(deleted ? createdAt.plusSeconds(60) : null)
                .build();
    }

    private ChatMessage chatMessage(
            String messageId,
            String content,
            LocalDateTime createdAt
    ) {
        return ChatMessage.builder()
                .id(messageId)
                .roomId(ROOM_ID)
                .writerId(WRITER_ID)
                .content(content)
                .createdAt(createdAt)
                .eventList(new ChatMessageEventList())
                .dlqEventList(new ChatMessageDlqEventList())
                .build();
    }
}