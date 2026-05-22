package chatmessage.service;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.example.chatmessage.adapter.in.ChatMessageGrpcService;
import org.example.chatmessage.application.port.out.ChatMessageCachePort;
import org.example.chatmessage.application.service.ChatMessageCommandService;
import org.example.chatmessage.domain.model.ChatMessage;
import org.example.chatroom.application.service.ChatRoomQueryService;
import org.example.chatroom.domain.model.ChatRoom;
import org.example.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.common.exception.ChatMessageCacheException;
import org.example.chatmessage.adapter.in.exception.ChatMessageGrpcCancelledException;
import org.example.chatroom.domain.exception.ChatRoomNotFoundException;
import org.example.grpc.chatmessage.ChatMessageGrpcRequest;
import org.example.grpc.chatmessage.ChatMessageGrpcResponse;
import org.example.grpc.chatmessage.ChatMessageHardDeleteGrpcRequest;
import org.example.grpc.chatmessage.ChatMessageHardDeleteGrpcResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageGrpcServiceTest {

    @Mock
    private ChatMessageCachePort cache;

    @Mock
    private ChatRoomQueryService chatRoomQueryService;

    @Mock
    private ChatMessageCommandService chatMessageCommandService;

    @InjectMocks
    private ChatMessageGrpcService sut;

    private final String messageId = "msg1";
    private final String roomId = "room1";
    private final String writerId = "user1";
    private final String content = "hello";
    private final String clientMessageId = "client-msg-1";

    private final ChatRoomCategory category = ChatRoomCategory.values()[0];

    @Test
    @DisplayName("채팅방 조회 결과가 null이면 ChatRoomNotFoundException을 던지고 캐시 저장과 응답 전송을 하지 않는다")
    void grpc_save_should_throw_when_chat_room_is_null() {
        // given
        ChatMessageGrpcRequest request = request();

        when(chatRoomQueryService.findById(roomId)).thenReturn(null);

        @SuppressWarnings("unchecked")
        StreamObserver<ChatMessageGrpcResponse> observer = mock(StreamObserver.class);

        // when & then
        assertThrows(ChatRoomNotFoundException.class,
                () -> sut.save(request, observer));

        verify(chatRoomQueryService).findById(roomId);

        verify(cache, never())
                .save(any(ChatMessage.class), any(ChatRoomCategory.class), anySet());

        verify(observer, never()).onNext(any());
        verify(observer, never()).onCompleted();
        verify(observer, never()).onError(any());
    }

    @Test
    @DisplayName("메시지 저장에 성공하면 캐시에 저장하고 성공 응답을 전송한다")
    void grpc_save_should_return_success_response_when_save_succeeds() {
        // given
        ChatMessageGrpcRequest request = request();

        ChatRoom chatRoom = mock(ChatRoom.class);
        Set<String> memberIds = Set.of("user1", "user2");

        when(chatRoomQueryService.findById(roomId)).thenReturn(chatRoom);
        when(chatRoom.getCategory()).thenReturn(category);
        when(chatRoom.getMemberIds()).thenReturn(memberIds);

        @SuppressWarnings("unchecked")
        StreamObserver<ChatMessageGrpcResponse> observer = mock(StreamObserver.class);

        // when
        sut.save(request, observer);

        // then
        ArgumentCaptor<ChatMessageGrpcResponse> responseCaptor =
                ArgumentCaptor.forClass(ChatMessageGrpcResponse.class);

        ArgumentCaptor<ChatMessage> messageCaptor =
                ArgumentCaptor.forClass(ChatMessage.class);

        verify(chatRoomQueryService).findById(roomId);
        verify(chatRoom).getCategory();
        verify(chatRoom).getMemberIds();

        verify(cache).save(
                messageCaptor.capture(),
                eq(category),
                eq(memberIds)
        );

        ChatMessage savedMessage = messageCaptor.getValue();

        assertEquals(messageId, savedMessage.getId());
        assertEquals(roomId, savedMessage.getRoomId());
        assertEquals(writerId, savedMessage.getWriterId());
        assertEquals(content, savedMessage.getContent());

        verify(observer).onNext(responseCaptor.capture());
        verify(observer).onCompleted();
        verify(observer, never()).onError(any());

        ChatMessageGrpcResponse response = responseCaptor.getValue();

        assertTrue(response.getSuccess());
        assertEquals(messageId, response.getId());
        assertTrue(response.getTs() > 0);
    }

    @Test
    @DisplayName("채팅방 조회 중 예외가 발생하면 예외를 전파하고 캐시 저장과 응답 전송을 하지 않는다")
    void grpc_save_should_throw_when_chat_room_lookup_fails() {
        // given
        ChatMessageGrpcRequest request = request();

        when(chatRoomQueryService.findById(roomId))
                .thenThrow(new RuntimeException("chat room lookup failed"));

        @SuppressWarnings("unchecked")
        StreamObserver<ChatMessageGrpcResponse> observer = mock(StreamObserver.class);

        // when & then
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> sut.save(request, observer));

        assertEquals("chat room lookup failed", ex.getMessage());

        verify(chatRoomQueryService).findById(roomId);

        verify(cache, never())
                .save(any(ChatMessage.class), any(ChatRoomCategory.class), anySet());

        verify(observer, never()).onNext(any());
        verify(observer, never()).onCompleted();
        verify(observer, never()).onError(any());
    }

    @Test
    @DisplayName("캐시 저장 중 예외가 발생하면 ChatMessageCacheException을 던지고 응답 전송을 하지 않는다")
    void grpc_save_should_throw_cache_exception_when_cache_save_fails() {
        // given
        ChatMessageGrpcRequest request = request();

        ChatRoom chatRoom = mock(ChatRoom.class);
        Set<String> memberIds = Set.of("user1", "user2");

        when(chatRoomQueryService.findById(roomId)).thenReturn(chatRoom);
        when(chatRoom.getCategory()).thenReturn(category);
        when(chatRoom.getMemberIds()).thenReturn(memberIds);

        doThrow(new RuntimeException("cache save failed"))
                .when(cache)
                .save(any(ChatMessage.class), eq(category), eq(memberIds));

        @SuppressWarnings("unchecked")
        StreamObserver<ChatMessageGrpcResponse> observer = mock(StreamObserver.class);

        // when & then
        assertThrows(ChatMessageCacheException.class,
                () -> sut.save(request, observer));

        verify(chatRoomQueryService).findById(roomId);
        verify(chatRoom).getCategory();
        verify(chatRoom).getMemberIds();

        verify(cache).save(
                any(ChatMessage.class),
                eq(category),
                eq(memberIds)
        );

        verify(observer, never()).onNext(any());
        verify(observer, never()).onCompleted();
        verify(observer, never()).onError(any());
    }

    @Test
    @DisplayName("채팅방 조회 전에 gRPC Context가 취소되면 ChatMessageGrpcCancelledException을 던지고 아무 작업도 하지 않는다")
    void grpc_save_should_throw_cancelled_exception_when_context_is_cancelled_before_chat_room_lookup() {
        // given
        ChatMessageGrpcRequest request = request();

        @SuppressWarnings("unchecked")
        StreamObserver<ChatMessageGrpcResponse> observer = mock(StreamObserver.class);

        Context.CancellableContext ctx = Context.current().withCancellation();
        ctx.cancel(null);

        try {
            assertThrows(ChatMessageGrpcCancelledException.class,
                    () -> ctx.run(() -> sut.save(request, observer)));
        } finally {
            ctx.close();
        }

        verifyNoInteractions(chatRoomQueryService, cache);

        verify(observer, never()).onNext(any());
        verify(observer, never()).onCompleted();
        verify(observer, never()).onError(any());
    }

    @Test
    @DisplayName("캐시 저장 전에 gRPC Context가 취소되면 ChatMessageGrpcCancelledException을 던지고 캐시 저장과 응답 전송을 하지 않는다")
    void grpc_save_should_throw_cancelled_exception_when_context_is_cancelled_before_cache_save() {
        // given
        ChatMessageGrpcRequest request = request();

        ChatRoom chatRoom = mock(ChatRoom.class);
        Set<String> memberIds = Set.of("user1", "user2");

        when(chatRoomQueryService.findById(roomId)).thenReturn(chatRoom);

        Context.CancellableContext ctx = Context.current().withCancellation();

        when(chatRoom.getMemberIds()).thenAnswer(invocation -> {
            ctx.cancel(null);
            return memberIds;
        });

        @SuppressWarnings("unchecked")
        StreamObserver<ChatMessageGrpcResponse> observer = mock(StreamObserver.class);

        try {
            assertThrows(ChatMessageGrpcCancelledException.class,
                    () -> ctx.run(() -> sut.save(request, observer)));
        } finally {
            ctx.close();
        }

        verify(chatRoomQueryService).findById(roomId);
        verify(chatRoom).getMemberIds();

        verify(cache, never())
                .save(any(ChatMessage.class), any(ChatRoomCategory.class), anySet());

        verify(observer, never()).onNext(any());
        verify(observer, never()).onCompleted();
        verify(observer, never()).onError(any());
    }

    @Test
    @DisplayName("하드 삭제 요청을 command service에 위임하고 성공 응답을 전송한다")
    void grpc_hard_delete_should_delegate_to_command_service_and_return_success_response() {
        // given
        ChatMessageHardDeleteGrpcRequest request = hardDeleteRequest();

        @SuppressWarnings("unchecked")
        StreamObserver<ChatMessageHardDeleteGrpcResponse> observer = mock(StreamObserver.class);

        // when
        sut.hardDelete(request, observer);

        // then
        ArgumentCaptor<ChatMessageHardDeleteGrpcResponse> captor =
                ArgumentCaptor.forClass(ChatMessageHardDeleteGrpcResponse.class);

        verify(chatMessageCommandService).hardDelete(messageId, roomId);

        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();
        verify(observer, never()).onError(any());

        ChatMessageHardDeleteGrpcResponse response = captor.getValue();

        assertTrue(response.getSuccess());
        assertEquals(messageId, response.getMessageId());
    }

    @Test
    @DisplayName("하드 삭제 중 command service 예외가 발생하면 예외를 전파하고 응답 전송을 하지 않는다")
    void grpc_hard_delete_should_throw_when_command_service_fails() {
        // given
        ChatMessageHardDeleteGrpcRequest request = hardDeleteRequest();

        doThrow(new RuntimeException("hard delete failed"))
                .when(chatMessageCommandService)
                .hardDelete(messageId, roomId);

        @SuppressWarnings("unchecked")
        StreamObserver<ChatMessageHardDeleteGrpcResponse> observer = mock(StreamObserver.class);

        // when & then
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> sut.hardDelete(request, observer));

        assertEquals("hard delete failed", ex.getMessage());

        verify(chatMessageCommandService).hardDelete(messageId, roomId);

        verify(observer, never()).onNext(any());
        verify(observer, never()).onCompleted();
        verify(observer, never()).onError(any());
    }

    private ChatMessageGrpcRequest request() {
        return ChatMessageGrpcRequest.newBuilder()
                .setMessageId(messageId)
                .setRoomId(roomId)
                .setWriterId(writerId)
                .setContent(content)
                .setClientMessageId(clientMessageId)
                .build();
    }

    private ChatMessageHardDeleteGrpcRequest hardDeleteRequest() {
        return ChatMessageHardDeleteGrpcRequest.newBuilder()
                .setMessageId(messageId)
                .setRoomId(roomId)
                .build();
    }
}