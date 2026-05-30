package chatroom;

import org.example.common.enums.StompTopic;
import org.example.contract.chatroom.MyChatRoomBadgeEvent;
import org.example.contract.chatroom.MyChatRoomPayload;
import org.example.websocket.gateway.adapter.in.event.chatroom.MyChatRoomBadgeEventHandler;
import org.example.websocket.gateway.adapter.in.event.chatroom.dto.MyChatRoomResponse;
import org.example.websocket.gateway.session.application.cache.LocalSessionCache;
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
class MyChatRoomBadgeEventHandlerTest {

    @Mock
    private SimpMessagingTemplate stompTemplate;

    @Mock
    private LocalSessionCache localSessionCache;

    @InjectMocks
    private MyChatRoomBadgeEventHandler sut;

    private final String instanceId = "instance-1";
    private final String txId = "tx-1";

    private final String roomId = "000000000000000000000001";
    private final String memberId = "member-1";
    private final String otherMemberId = "member-2";

    private final String lastMsgContent = "마지막 메시지";
    private final Instant lastMsgCreatedAt = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sut, "instanceId", instanceId);
    }

    @Test
    @DisplayName("로컬 세션이 있는 멤버에게만 채팅방 badge 메시지를 전송한다")
    void handleSendOnlyToLocalMembers() {
        MyChatRoomBadgeEvent event = event(Set.of(memberId, otherMemberId));

        given(localSessionCache.hasUser(memberId)).willReturn(true);
        given(localSessionCache.hasUser(otherMemberId)).willReturn(false);

        sut.handle(event, txId);

        ArgumentCaptor<MyChatRoomResponse> responseCaptor = ArgumentCaptor.forClass(MyChatRoomResponse.class);

        verify(stompTemplate).convertAndSendToUser(
                eq(memberId),
                eq(StompTopic.CHAT_ROOM_BADGE.getPrefix()),
                responseCaptor.capture()
        );

        MyChatRoomResponse response = responseCaptor.getValue();

        assertThat(response.id()).isEqualTo(roomId);
        assertThat(response.lastMsgContent()).isEqualTo(lastMsgContent);
        assertThat(response.lastMsgCreatedAt()).isEqualTo(lastMsgCreatedAt.toEpochMilli());

        verify(stompTemplate, never()).convertAndSendToUser(
                eq(otherMemberId),
                anyString(),
                any()
        );
    }

    @Test
    @DisplayName("로컬 세션이 있는 멤버가 없으면 STOMP 전송을 하지 않는다")
    void handleSkipWhenNoLocalMemberExists() {
        MyChatRoomBadgeEvent event = event(Set.of(memberId, otherMemberId));

        given(localSessionCache.hasUser(memberId)).willReturn(false);
        given(localSessionCache.hasUser(otherMemberId)).willReturn(false);

        sut.handle(event, txId);

        verify(localSessionCache).hasUser(memberId);
        verify(localSessionCache).hasUser(otherMemberId);
        verifyNoInteractions(stompTemplate);
    }

    @Test
    @DisplayName("일부 멤버 전송 중 예외가 발생해도 다른 멤버 전송은 계속 수행한다")
    void handleContinueWhenSendToOneMemberFails() {
        MyChatRoomBadgeEvent event = event(Set.of(memberId, otherMemberId));

        given(localSessionCache.hasUser(memberId)).willReturn(true);
        given(localSessionCache.hasUser(otherMemberId)).willReturn(true);

        doThrow(new RuntimeException("stomp failed"))
                .when(stompTemplate)
                .convertAndSendToUser(
                        eq(memberId),
                        eq(StompTopic.CHAT_ROOM_BADGE.getPrefix()),
                        any(MyChatRoomResponse.class)
                );

        assertDoesNotThrow(() -> sut.handle(event, txId));

        verify(stompTemplate).convertAndSendToUser(
                eq(memberId),
                eq(StompTopic.CHAT_ROOM_BADGE.getPrefix()),
                any(MyChatRoomResponse.class)
        );

        verify(stompTemplate).convertAndSendToUser(
                eq(otherMemberId),
                eq(StompTopic.CHAT_ROOM_BADGE.getPrefix()),
                any(MyChatRoomResponse.class)
        );
    }

    @Test
    @DisplayName("memberIds가 비어 있으면 STOMP 전송을 하지 않는다")
    void handleSkipWhenMemberIdsIsEmpty() {
        MyChatRoomBadgeEvent event = event(Set.of());

        sut.handle(event, txId);

        verifyNoInteractions(localSessionCache);
        verifyNoInteractions(stompTemplate);
    }

    private MyChatRoomBadgeEvent event(Set<String> memberIds) {
        return new MyChatRoomBadgeEvent(payload(memberIds));
    }

    private MyChatRoomPayload payload(Set<String> memberIds) {
        return MyChatRoomPayload.ofLastMessage(roomId, memberIds, lastMsgContent, lastMsgCreatedAt);
    }
}