package chatmessage.service;

import org.example.chatmessage.adapter.dto.ChatMessageDlqEventList;
import org.example.chatmessage.adapter.dto.ChatMessageEventList;
import org.example.chatmessage.adapter.dto.ChatMessagePayload;
import org.example.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chatmessage.application.service.ChatMessageEventService;
import org.example.chatmessage.domain.model.ChatMessage;
import org.example.chatmessage.domain.model.event.ChatMessagePersistEvent;
import org.example.chatroom.application.port.out.ChatRoomPersistencePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageEventServiceTest {

    @Mock
    private ChatMessagePersistencePort chatMessagePersistencePort;

    @Mock
    private ChatRoomPersistencePort chatRoomPersistencePort;

    @InjectMocks
    private ChatMessageEventService sut;

    private final String txId = "tx-1";

    private final String messageId = "100000000000000000000001";
    private final String roomId = "000000000000000000000001";
    private final String writerId = "writer-1";
    private final String content = "hello";

    private final String memberId1 = "member-1";
    private final String memberId2 = "member-2";

    private final Set<String> memberIds = Set.of(memberId1, memberId2);

    private final LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);

    @Nested
    @DisplayName("handle")
    class HandleTest {

        @Test
        @DisplayName("persist event를 처리하면 메시지를 저장하고 방 msgCnt와 membership score를 갱신한다")
        void handleSuccess() {
            // given
            ChatMessage domain = chatMessage();
            ChatMessagePersistEvent event = persistEvent(domain, memberIds);

            long createdAtMillis = domain.toEpochMillis();

            // when
            sut.handle(event, txId);

            // then
            ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);

            verify(chatMessagePersistencePort).save(messageCaptor.capture());

            ChatMessage saved = messageCaptor.getValue();

            assertThat(saved.getId()).isEqualTo(messageId);
            assertThat(saved.getRoomId()).isEqualTo(roomId);
            assertThat(saved.getWriterId()).isEqualTo(writerId);
            assertThat(saved.getContent()).isEqualTo(content);

            verify(chatRoomPersistencePort).incrementMsgCnt(roomId);
            verify(chatRoomPersistencePort).updateMembershipScores(
                    roomId,
                    memberIds,
                    createdAtMillis
            );
        }

        @Test
        @DisplayName("메시지 저장 중 DuplicateKeyException이 발생하면 중복 이벤트로 보고 이후 작업을 스킵한다")
        void handleDuplicateKeyException() {
            // given
            ChatMessage domain = chatMessage();
            ChatMessagePersistEvent event = persistEvent(domain, memberIds);

            doThrow(new DuplicateKeyException("duplicate message"))
                    .when(chatMessagePersistencePort)
                    .save(any(ChatMessage.class));

            // when
            sut.handle(event, txId);

            // then
            verify(chatMessagePersistencePort).save(any(ChatMessage.class));

            verify(chatRoomPersistencePort, never()).incrementMsgCnt(anyString());
            verify(chatRoomPersistencePort, never())
                    .updateMembershipScores(anyString(), anySet(), anyLong());
        }

        @Test
        @DisplayName("메시지 저장 중 RuntimeException이 발생하면 예외를 전파하고 이후 작업을 하지 않는다")
        void handleSaveRuntimeException() {
            // given
            ChatMessage domain = chatMessage();
            ChatMessagePersistEvent event = persistEvent(domain, memberIds);

            RuntimeException exception = new RuntimeException("mongo save failed");

            doThrow(exception)
                    .when(chatMessagePersistencePort)
                    .save(any(ChatMessage.class));

            // when & then
            assertThatThrownBy(() -> sut.handle(event, txId))
                    .isSameAs(exception);

            verify(chatMessagePersistencePort).save(any(ChatMessage.class));
            verify(chatRoomPersistencePort, never()).incrementMsgCnt(anyString());
            verify(chatRoomPersistencePort, never())
                    .updateMembershipScores(anyString(), anySet(), anyLong());
        }

        @Test
        @DisplayName("msgCnt 증가 중 예외가 발생하면 membership score 갱신을 수행하지 않고 예외를 전파한다")
        void handleIncrementMsgCntException() {
            // given
            ChatMessage domain = chatMessage();
            ChatMessagePersistEvent event = persistEvent(domain, memberIds);

            RuntimeException exception = new RuntimeException("increment failed");

            doThrow(exception)
                    .when(chatRoomPersistencePort)
                    .incrementMsgCnt(roomId);

            // when & then
            assertThatThrownBy(() -> sut.handle(event, txId))
                    .isSameAs(exception);

            verify(chatMessagePersistencePort).save(any(ChatMessage.class));
            verify(chatRoomPersistencePort).incrementMsgCnt(roomId);
            verify(chatRoomPersistencePort, never())
                    .updateMembershipScores(anyString(), anySet(), anyLong());
        }

        @Test
        @DisplayName("membership score 갱신 중 예외가 발생하면 예외를 전파한다")
        void handleUpdateMembershipScoresException() {
            // given
            ChatMessage domain = chatMessage();
            ChatMessagePersistEvent event = persistEvent(domain, memberIds);

            RuntimeException exception = new RuntimeException("membership score update failed");

            doThrow(exception)
                    .when(chatRoomPersistencePort)
                    .updateMembershipScores(eq(roomId), eq(memberIds), anyLong());

            // when & then
            assertThatThrownBy(() -> sut.handle(event, txId))
                    .isSameAs(exception);

            verify(chatMessagePersistencePort).save(any(ChatMessage.class));
            verify(chatRoomPersistencePort).incrementMsgCnt(roomId);
            verify(chatRoomPersistencePort).updateMembershipScores(
                    eq(roomId),
                    eq(memberIds),
                    anyLong()
            );
        }
    }

    @Nested
    @DisplayName("recover")
    class RecoverTest {

        @Test
        @DisplayName("recover 호출 시 recoverPersist를 수행하고 예외 없이 종료한다")
        void recover() {
            // given
            ChatMessage domain = chatMessage();
            ChatMessagePersistEvent event = persistEvent(domain, memberIds);

            RuntimeException exception = new RuntimeException("mongo failed");

            // when & then
            assertThatCode(() -> sut.recover(exception, event, txId))
                    .doesNotThrowAnyException();
        }
    }

    private ChatMessage chatMessage() {
        return ChatMessage.builder()
                .id(messageId)
                .roomId(roomId)
                .writerId(writerId)
                .content(content)
                .createdAt(createdAt)
                .eventList(new ChatMessageEventList())
                .dlqEventList(new ChatMessageDlqEventList())
                .build();
    }

    private ChatMessagePersistEvent persistEvent(ChatMessage domain, Set<String> memberIds) {
        return new ChatMessagePersistEvent(
                ChatMessagePayload.fromDomain(domain),
                memberIds
        );
    }
}