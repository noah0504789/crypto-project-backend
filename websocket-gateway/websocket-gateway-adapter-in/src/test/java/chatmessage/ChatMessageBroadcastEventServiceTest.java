package chatmessage;

import org.example.common.enums.StompTopic;
import org.example.contract.chatmessage.ChatMessageBroadcastEvent;
import org.example.contract.chatmessage.ChatMessagePayload;
import org.example.event.chatmessage.ChatMessageBroadcastEventService;
import org.example.event.chatmessage.dto.ChatMessageResponse;
import org.example.session.LocalSessionCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageBroadcastEventServiceTest {

    @Mock
    private SimpMessagingTemplate stompTemplate;

    @Mock
    private LocalSessionCache localSessionCache;

    @InjectMocks
    private ChatMessageBroadcastEventService sut;

    private final String txId = "tx-1";
    private final String roomId = "000000000000000000000001";
    private final String messageId = "100000000000000000000001";
    private final String writerId = "writer-1";
    private final String content = "hello";
    private final String clientMessageId = "client-message-1";

    private final String localMemberId = "member-1";
    private final String remoteMemberId = "member-2";

    private final Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sut, "instanceIndex", "instance-1");
    }

    @Test
    @DisplayName("로컬 서버에 접속 중인 멤버가 있으면 STOMP destination으로 메시지를 전송한다")
    void handleBroadcastWhenAnyLocalMemberExists() {
        // given
        ChatMessageBroadcastEvent event = event(Set.of(localMemberId));

        given(localSessionCache.hasUser(localMemberId)).willReturn(true);

        // when
        sut.handle(event, txId);

        // then
        String destination = StompTopic.CHAT_ROOM.destination(roomId);

        ArgumentCaptor<ChatMessageResponse> responseCaptor =
                ArgumentCaptor.forClass(ChatMessageResponse.class);

        verify(stompTemplate).convertAndSend(eq(destination), responseCaptor.capture());

        ChatMessageResponse response = responseCaptor.getValue();

        assertThat(response.clientMessageId()).isEqualTo(clientMessageId);
        assertThat(response.id()).isEqualTo(messageId);
        assertThat(response.writerId()).isEqualTo(writerId);
        assertThat(response.content()).isEqualTo(content);
        assertThat(response.createdAt()).isEqualTo(createdAt.toEpochMilli());
    }

    @Test
    @DisplayName("memberIds가 null이면 STOMP 전송을 하지 않는다")
    void handleSkipWhenMemberIdsIsNull() {
        // given
        ChatMessageBroadcastEvent event = event(null);

        // when
        sut.handle(event, txId);

        // then
        verifyNoInteractions(localSessionCache);
        verifyNoInteractions(stompTemplate);
    }

    @Test
    @DisplayName("memberIds가 비어 있으면 STOMP 전송을 하지 않는다")
    void handleSkipWhenMemberIdsIsEmpty() {
        // given
        ChatMessageBroadcastEvent event = event(Set.of());

        // when
        sut.handle(event, txId);

        // then
        verifyNoInteractions(localSessionCache);
        verifyNoInteractions(stompTemplate);
    }

    @Test
    @DisplayName("로컬 서버에 접속 중인 멤버가 없으면 STOMP 전송을 하지 않는다")
    void handleSkipWhenNoLocalMemberExists() {
        // given
        ChatMessageBroadcastEvent event = event(Set.of(remoteMemberId));

        given(localSessionCache.hasUser(remoteMemberId)).willReturn(false);

        // when
        sut.handle(event, txId);

        // then
        verify(localSessionCache).hasUser(remoteMemberId);
        verifyNoInteractions(stompTemplate);
    }

    @Test
    @DisplayName("STOMP 전송 중 예외가 발생해도 예외를 밖으로 던지지 않는다")
    void handleDoesNotThrowWhenStompSendFails() {
        // given
        ChatMessageBroadcastEvent event = event(Set.of(localMemberId));

        given(localSessionCache.hasUser(localMemberId)).willReturn(true);

        doThrow(new RuntimeException("stomp failed"))
                .when(stompTemplate)
                .convertAndSend(anyString(), any(ChatMessageResponse.class));

        // when & then
        assertDoesNotThrow(() -> sut.handle(event, txId));

        verify(localSessionCache).hasUser(localMemberId);
        verify(stompTemplate).convertAndSend(
                eq(StompTopic.CHAT_ROOM.destination(roomId)),
                any(ChatMessageResponse.class)
        );
    }

    private ChatMessageBroadcastEvent event(Set<String> memberIds) {
        return new ChatMessageBroadcastEvent(
                payload(),
                memberIds,
                clientMessageId
        );
    }

    private ChatMessagePayload payload() {
        return new ChatMessagePayload(messageId, roomId, writerId, content, createdAt);
    }
}
