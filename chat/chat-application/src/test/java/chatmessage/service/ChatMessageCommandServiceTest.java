package chatmessage.service;

import org.example.chat.common.exception.ChatMessageCacheException;
import org.example.chatmessage.application.dto.ChatMessageSaveCommand;
import org.example.chatmessage.application.dto.ChatMessageSaveResult;
import org.example.chatmessage.domain.event.dlq.ChatMessageDlqEventList;
import org.example.chatmessage.domain.event.ChatMessageEventList;
import org.example.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chatmessage.application.service.ChatMessageCommandService;
import org.example.chatmessage.domain.model.ChatMessage;
import org.example.chatroom.application.dto.ChatRoomMembershipScore;
import org.example.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chatroom.domain.exception.ChatRoomMembershipNotFoundException;
import org.example.chatroom.domain.exception.ChatRoomNotFoundException;
import org.example.chatroom.domain.model.ChatRoom;
import org.example.chatroom.domain.model.ChatRoomCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageCommandServiceTest {

    @Mock
    private ChatMessagePersistencePort chatMessagePersistencePort;

    @Mock
    private ChatRoomPersistencePort chatRoomPersistencePort;

    @Mock
    private ChatMessageCachePort chatMessageCachePort;

    @InjectMocks
    private ChatMessageCommandService sut;

    private final String messageId = "100000000000000000000001";
    private final String roomId = "000000000000000000000001";
    private final String writerId = "writer-1";
    private final String content = "hello";
    private final String clientMessageId = "client-msg-1";

    private final Instant latestCreatedAt = Instant.parse("2026-01-01T03:00:00Z");
    private final long latestCreatedAtMillis = latestCreatedAt.toEpochMilli();

    private final String memberId1 = "member-1";
    private final String memberId2 = "member-2";

    @Nested
    @DisplayName("save")
    class SaveTest {

        @Test
        @DisplayName("채팅방이 존재하고 작성자가 멤버이면 메시지를 저장 이벤트로 발행하고 cache에 저장한 뒤 결과를 반환한다")
        void saveSuccess() {
            // given
            ChatRoom chatRoom = chatRoom(Set.of(writerId, "user-2"));

            ChatMessageSaveCommand command = new ChatMessageSaveCommand(
                    messageId,
                    roomId,
                    writerId,
                    content,
                    clientMessageId
            );

            given(chatRoomPersistencePort.findById(roomId))
                    .willReturn(Optional.of(chatRoom));

            // when
            ChatMessageSaveResult result = sut.save(command);

            // then
            assertThat(result.id()).isEqualTo(messageId);
            assertThat(result.ts()).isGreaterThan(0L);

            ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);

            verify(chatRoomPersistencePort).findById(roomId);
            verify(chatMessageCachePort).save(
                    messageCaptor.capture(),
                    eq(chatRoom.getCategory()),
                    eq(chatRoom.getMemberIds())
            );

            ChatMessage savedMessage = messageCaptor.getValue();

            assertThat(savedMessage.getId()).isEqualTo(messageId);
            assertThat(savedMessage.getRoomId()).isEqualTo(roomId);
            assertThat(savedMessage.getWriterId()).isEqualTo(writerId);
            assertThat(savedMessage.getContent()).isEqualTo(content);
        }

        @Test
        @DisplayName("채팅방이 없으면 ChatRoomNotFoundException을 던지고 cache 저장을 수행하지 않는다")
        void saveThrowsWhenChatRoomNotFound() {
            // given
            ChatMessageSaveCommand command = new ChatMessageSaveCommand(
                    messageId,
                    roomId,
                    writerId,
                    content,
                    clientMessageId
            );

            given(chatRoomPersistencePort.findById(roomId))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> sut.save(command))
                    .isInstanceOf(ChatRoomNotFoundException.class);

            verify(chatRoomPersistencePort).findById(roomId);
            verify(chatMessageCachePort, never())
                    .save(any(ChatMessage.class), any(ChatRoomCategory.class), anySet());
        }

        @Test
        @DisplayName("작성자가 채팅방 멤버가 아니면 ChatRoomMembershipNotFoundException을 던지고 cache 저장을 수행하지 않는다")
        void saveThrowsWhenWriterIsNotMember() {
            // given
            ChatRoom chatRoom = chatRoom(Set.of("other-user"));

            ChatMessageSaveCommand command = new ChatMessageSaveCommand(
                    messageId,
                    roomId,
                    writerId,
                    content,
                    clientMessageId
            );

            given(chatRoomPersistencePort.findById(roomId))
                    .willReturn(Optional.of(chatRoom));

            // when & then
            assertThatThrownBy(() -> sut.save(command))
                    .isInstanceOf(ChatRoomMembershipNotFoundException.class);

            verify(chatRoomPersistencePort).findById(roomId);
            verify(chatMessageCachePort, never())
                    .save(any(ChatMessage.class), any(ChatRoomCategory.class), anySet());
        }

        @Test
        @DisplayName("cache 저장 중 예외가 발생하면 ChatMessageCacheException을 던진다")
        void saveThrowsChatMessageCacheExceptionWhenCacheSaveFails() {
            // given
            ChatRoom chatRoom = chatRoom(Set.of(writerId, "user-2"));

            ChatMessageSaveCommand command = new ChatMessageSaveCommand(
                    messageId,
                    roomId,
                    writerId,
                    content,
                    clientMessageId
            );

            given(chatRoomPersistencePort.findById(roomId))
                    .willReturn(Optional.of(chatRoom));

            doThrow(new RuntimeException("cache save failed"))
                    .when(chatMessageCachePort)
                    .save(any(ChatMessage.class), eq(chatRoom.getCategory()), eq(chatRoom.getMemberIds()));

            // when & then
            assertThatThrownBy(() -> sut.save(command))
                    .isInstanceOf(ChatMessageCacheException.class)
                    .hasMessageContaining("failed during cache save");

            verify(chatRoomPersistencePort).findById(roomId);
            verify(chatMessageCachePort)
                    .save(any(ChatMessage.class), eq(chatRoom.getCategory()), eq(chatRoom.getMemberIds()));
        }
    }

    @Nested
    @DisplayName("hardDelete")
    class HardDeleteTest {

        @Test
        @DisplayName("메시지 hardDelete 성공 시 msgCnt 감소, membership score 갱신, cache 삭제를 수행한다")
        void hardDeleteSuccess() {
            // given
            ChatMessage latestMessage = chatMessage("100000000000000000000002", latestCreatedAt);

            List<ChatRoomMembershipScore> chatRoomMembershipScores = List.of(
                    new ChatRoomMembershipScore(memberId1, latestCreatedAtMillis),
                    new ChatRoomMembershipScore(memberId2, 0L)
            );

            given(chatMessagePersistencePort.hardDelete(messageId))
                    .willReturn(true);

            given(chatMessagePersistencePort.findLatestExcluding(roomId, messageId))
                    .willReturn(Optional.of(latestMessage));

            given(chatRoomPersistencePort.refreshMembershipScores(roomId, latestCreatedAtMillis))
                    .willReturn(chatRoomMembershipScores);

            // when
            sut.hardDelete(messageId, roomId);

            // then
            verify(chatMessagePersistencePort).hardDelete(messageId);
            verify(chatRoomPersistencePort).decrementMsgCnt(roomId);
            verify(chatMessagePersistencePort).findLatestExcluding(roomId, messageId);
            verify(chatRoomPersistencePort).refreshMembershipScores(roomId, latestCreatedAtMillis);
            verify(chatMessageCachePort).hardDelete(messageId, roomId, chatRoomMembershipScores);
        }

        @Test
        @DisplayName("Mongo에서 삭제할 메시지가 없으면 이후 작업을 수행하지 않는다")
        void hardDeleteSkippedWhenMongoMessageNotFound() {
            // given
            given(chatMessagePersistencePort.hardDelete(messageId))
                    .willReturn(false);

            // when
            sut.hardDelete(messageId, roomId);

            // then
            verify(chatMessagePersistencePort).hardDelete(messageId);

            verify(chatRoomPersistencePort, never()).decrementMsgCnt(anyString());
            verify(chatMessagePersistencePort, never()).findLatestExcluding(anyString(), anyString());
            verify(chatRoomPersistencePort, never()).refreshMembershipScores(anyString(), anyLong());
            verify(chatMessageCachePort, never()).hardDelete(anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("삭제 후 남은 최신 메시지가 없으면 fallbackMsgCreatedAt=0으로 membership score를 갱신한다")
        void hardDeleteWithNoLatestMessage() {
            // given
            List<ChatRoomMembershipScore> chatRoomMembershipScores = List.of(
                    new ChatRoomMembershipScore(memberId1, 0L),
                    new ChatRoomMembershipScore(memberId2, 0L)
            );

            given(chatMessagePersistencePort.hardDelete(messageId))
                    .willReturn(true);

            given(chatMessagePersistencePort.findLatestExcluding(roomId, messageId))
                    .willReturn(Optional.empty());

            given(chatRoomPersistencePort.refreshMembershipScores(roomId, 0L))
                    .willReturn(chatRoomMembershipScores);

            // when
            sut.hardDelete(messageId, roomId);

            // then
            verify(chatRoomPersistencePort).decrementMsgCnt(roomId);
            verify(chatMessagePersistencePort).findLatestExcluding(roomId, messageId);
            verify(chatRoomPersistencePort).refreshMembershipScores(roomId, 0L);
            verify(chatMessageCachePort).hardDelete(messageId, roomId, chatRoomMembershipScores);
        }

        @Test
        @DisplayName("cache hardDelete가 실패해도 예외를 전파하지 않는다")
        void hardDeleteCacheFailsButDoesNotThrow() {
            // given
            ChatMessage latestMessage = chatMessage("100000000000000000000002", latestCreatedAt);

            List<ChatRoomMembershipScore> chatRoomMembershipScores = List.of(
                    new ChatRoomMembershipScore(memberId1, latestCreatedAtMillis)
            );

            given(chatMessagePersistencePort.hardDelete(messageId))
                    .willReturn(true);

            given(chatMessagePersistencePort.findLatestExcluding(roomId, messageId))
                    .willReturn(Optional.of(latestMessage));

            given(chatRoomPersistencePort.refreshMembershipScores(roomId, latestCreatedAtMillis))
                    .willReturn(chatRoomMembershipScores);

            doThrow(new RuntimeException("redis delete failed"))
                    .when(chatMessageCachePort)
                    .hardDelete(messageId, roomId, chatRoomMembershipScores);

            // when & then
            assertThatCode(() -> sut.hardDelete(messageId, roomId))
                    .doesNotThrowAnyException();

            verify(chatMessageCachePort).hardDelete(messageId, roomId, chatRoomMembershipScores);
        }

        @Test
        @DisplayName("Mongo hardDelete 중 예외가 발생하면 예외를 전파한다")
        void hardDeleteThrowsWhenMongoDeleteFails() {
            // given
            RuntimeException exception = new RuntimeException("mongo hardDelete failed");

            given(chatMessagePersistencePort.hardDelete(messageId))
                    .willThrow(exception);

            // when & then
            assertThatThrownBy(() -> sut.hardDelete(messageId, roomId))
                    .isSameAs(exception);

            verify(chatRoomPersistencePort, never()).decrementMsgCnt(anyString());
            verify(chatMessageCachePort, never()).hardDelete(anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("msgCnt 감소 중 예외가 발생하면 이후 작업을 수행하지 않고 예외를 전파한다")
        void hardDeleteThrowsWhenDecrementMsgCntFails() {
            // given
            RuntimeException exception = new RuntimeException("decrement failed");

            given(chatMessagePersistencePort.hardDelete(messageId))
                    .willReturn(true);

            doThrow(exception)
                    .when(chatRoomPersistencePort)
                    .decrementMsgCnt(roomId);

            // when & then
            assertThatThrownBy(() -> sut.hardDelete(messageId, roomId))
                    .isSameAs(exception);

            verify(chatMessagePersistencePort).hardDelete(messageId);
            verify(chatRoomPersistencePort).decrementMsgCnt(roomId);

            verify(chatMessagePersistencePort, never()).findLatestExcluding(anyString(), anyString());
            verify(chatRoomPersistencePort, never()).refreshMembershipScores(anyString(), anyLong());
            verify(chatMessageCachePort, never()).hardDelete(anyString(), anyString(), anyList());
        }

        @Test
        @DisplayName("membership score refresh 중 예외가 발생하면 cache 삭제를 수행하지 않고 예외를 전파한다")
        void hardDeleteThrowsWhenRefreshMembershipScoresFails() {
            // given
            ChatMessage latestMessage = chatMessage("100000000000000000000002", latestCreatedAt);
            RuntimeException exception = new RuntimeException("refresh membership failed");

            given(chatMessagePersistencePort.hardDelete(messageId))
                    .willReturn(true);

            given(chatMessagePersistencePort.findLatestExcluding(roomId, messageId))
                    .willReturn(Optional.of(latestMessage));

            given(chatRoomPersistencePort.refreshMembershipScores(roomId, latestCreatedAtMillis))
                    .willThrow(exception);

            // when & then
            assertThatThrownBy(() -> sut.hardDelete(messageId, roomId))
                    .isSameAs(exception);

            verify(chatMessagePersistencePort).hardDelete(messageId);
            verify(chatRoomPersistencePort).decrementMsgCnt(roomId);
            verify(chatMessagePersistencePort).findLatestExcluding(roomId, messageId);
            verify(chatRoomPersistencePort).refreshMembershipScores(roomId, latestCreatedAtMillis);

            verify(chatMessageCachePort, never()).hardDelete(anyString(), anyString(), anyList());
        }
    }

    private ChatRoom chatRoom(Set<String> memberIds) {
        return ChatRoom.rehydrate(
                roomId,
                "host-1",
                "테스트방",
                "테스트 설명",
                ChatRoomCategory.FREE,
                memberIds,
                0L,
                LocalDateTime.now()
        );
    }

    private ChatMessage chatMessage(String id, Instant createdAt) {
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
}