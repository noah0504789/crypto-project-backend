package stomp;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.example.StompController;
import org.example.event.chatmessage.ChatMessageGatewayClient;
import org.example.event.chatmessage.dto.ChatMessageAck;
import org.example.event.chatmessage.dto.ChatMessageRequest;
import org.example.grpc.chatmessage.ChatMessageGrpcResponse;
import org.example.grpc.chatmessage.ChatMessageHardDeleteGrpcResponse;
import org.example.infra.monitoring.GrpcMetricsRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StompControllerTest {

    @Mock
    private Validator validator;

    @Mock
    private SimpMessagingTemplate stompTemplate;

    @Mock
    private ChatMessageGatewayClient chatMessageGrpcClient;

    @Mock
    private GrpcMetricsRecorder grpcMetrics;

    @InjectMocks
    private StompController sut;

    private final String clientMessageId = "client-message-1";
    private final String roomId = "000000000000000000000001";
    private final String writerId = "writer-1";
    private final String content = "hello";
    private final String ackDestination = "/queue/chat/ack";

    @Test
    @DisplayName("요청 검증에 실패하면 error ack를 전송하고 gRPC save를 호출하지 않는다")
    void chatMessageValidationFailed() {
        // given
        ChatMessageRequest request = request();

        @SuppressWarnings("unchecked")
        ConstraintViolation<ChatMessageRequest> violation = mock(ConstraintViolation.class);

        given(validator.validate(request))
                .willReturn(Set.of(violation));

        // when
        sut.chatMessage(request);

        // then
        verify(stompTemplate).convertAndSendToUser(
                eq(writerId),
                eq(ackDestination),
                any(ChatMessageAck.class)
        );

        verify(chatMessageGrpcClient, never())
                .save(any(), anyString(), any());
    }

    @Test
    @DisplayName("요청 검증에 성공하면 messageId를 생성하고 gRPC save를 호출한다")
    void chatMessageValidationSuccess() {
        // given
        ChatMessageRequest request = request();

        given(validator.validate(request))
                .willReturn(Set.of());

        // when
        sut.chatMessage(request);

        // then
        ArgumentCaptor<String> messageIdCaptor = ArgumentCaptor.forClass(String.class);

        verify(chatMessageGrpcClient).save(
                eq(request),
                messageIdCaptor.capture(),
                any()
        );

        assertThat(messageIdCaptor.getValue()).isNotBlank();

        verifyNoInteractions(stompTemplate);
    }

    @Test
    @DisplayName("gRPC save 성공 콜백을 받으면 ok ack를 전송한다")
    void saveObserverOnNextSendsOkAck() {
        // given
        ChatMessageRequest request = request();

        given(validator.validate(request))
                .willReturn(Set.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<StreamObserver<ChatMessageGrpcResponse>> observerCaptor =
                ArgumentCaptor.forClass(StreamObserver.class);

        sut.chatMessage(request);

        verify(chatMessageGrpcClient).save(
                eq(request),
                anyString(),
                observerCaptor.capture()
        );

        ChatMessageGrpcResponse response = ChatMessageGrpcResponse.newBuilder()
                .setSuccess(true)
                .setId("message-1")
                .setTs(1234L)
                .build();

        // when
        observerCaptor.getValue().onNext(response);

        // then
        verify(stompTemplate).convertAndSendToUser(
                eq(writerId),
                eq(ackDestination),
                any(ChatMessageAck.class)
        );
    }

    @Test
    @DisplayName("gRPC save가 DEADLINE_EXCEEDED로 실패하면 metric 기록, hardDelete 요청, fail ack를 전송한다")
    void saveObserverOnErrorDeadlineExceeded() {
        // given
        ChatMessageRequest request = request();

        given(validator.validate(request))
                .willReturn(Set.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<StreamObserver<ChatMessageGrpcResponse>> observerCaptor =
                ArgumentCaptor.forClass(StreamObserver.class);

        ArgumentCaptor<String> messageIdCaptor = ArgumentCaptor.forClass(String.class);

        sut.chatMessage(request);

        verify(chatMessageGrpcClient).save(
                eq(request),
                messageIdCaptor.capture(),
                observerCaptor.capture()
        );

        String messageId = messageIdCaptor.getValue();

        Throwable error = Status.DEADLINE_EXCEEDED
                .withDescription("deadline")
                .asRuntimeException();

        // when
        observerCaptor.getValue().onError(error);

        // then
        verify(grpcMetrics).recordChatMessageSaveError(Status.Code.DEADLINE_EXCEEDED);

        verify(chatMessageGrpcClient).hardDelete(
                eq(messageId),
                eq(roomId),
                any()
        );

        verify(stompTemplate).convertAndSendToUser(
                eq(writerId),
                eq(ackDestination),
                any(ChatMessageAck.class)
        );
    }

    @Test
    @DisplayName("gRPC save가 CANCELLED로 실패하면 metric을 기록하고 hardDelete 없이 fail ack를 전송한다")
    void saveObserverOnErrorCancelled() {
        // given
        ChatMessageRequest request = request();

        given(validator.validate(request))
                .willReturn(Set.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<StreamObserver<ChatMessageGrpcResponse>> observerCaptor =
                ArgumentCaptor.forClass(StreamObserver.class);

        sut.chatMessage(request);

        verify(chatMessageGrpcClient).save(
                eq(request),
                anyString(),
                observerCaptor.capture()
        );

        Throwable error = Status.CANCELLED
                .withDescription("cancelled")
                .asRuntimeException();

        // when
        observerCaptor.getValue().onError(error);

        // then
        verify(grpcMetrics).recordChatMessageSaveError(Status.Code.CANCELLED);

        verify(chatMessageGrpcClient, never()).hardDelete(anyString(), anyString(), any());

        verify(stompTemplate).convertAndSendToUser(
                eq(writerId),
                eq(ackDestination),
                any(ChatMessageAck.class)
        );
    }

    @Test
    @DisplayName("gRPC save가 일반 오류로 실패하면 metric과 hardDelete 없이 SAVE_FAILED ack를 전송한다")
    void saveObserverOnErrorInternal() {
        // given
        ChatMessageRequest request = request();

        given(validator.validate(request))
                .willReturn(Set.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<StreamObserver<ChatMessageGrpcResponse>> observerCaptor =
                ArgumentCaptor.forClass(StreamObserver.class);

        sut.chatMessage(request);

        verify(chatMessageGrpcClient).save(
                eq(request),
                anyString(),
                observerCaptor.capture()
        );

        Throwable error = Status.INTERNAL
                .withDescription("internal")
                .asRuntimeException();

        // when
        observerCaptor.getValue().onError(error);

        // then
        verifyNoInteractions(grpcMetrics);

        verify(chatMessageGrpcClient, never()).hardDelete(anyString(), anyString(), any());

        verify(stompTemplate).convertAndSendToUser(
                eq(writerId),
                eq(ackDestination),
                any(ChatMessageAck.class)
        );
    }

    @Test
    @DisplayName("hardDelete 콜백이 DEADLINE_EXCEEDED로 실패하면 hardDelete metric을 기록한다")
    void hardDeleteObserverOnErrorDeadlineExceeded() {
        // given
        ChatMessageRequest request = request();

        given(validator.validate(request))
                .willReturn(Set.of());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<StreamObserver<ChatMessageGrpcResponse>> saveObserverCaptor =
                ArgumentCaptor.forClass(StreamObserver.class);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<StreamObserver<ChatMessageHardDeleteGrpcResponse>> hardDeleteObserverCaptor =
                ArgumentCaptor.forClass(StreamObserver.class);

        sut.chatMessage(request);

        verify(chatMessageGrpcClient).save(
                eq(request),
                anyString(),
                saveObserverCaptor.capture()
        );

        Throwable saveError = Status.DEADLINE_EXCEEDED
                .withDescription("deadline")
                .asRuntimeException();

        saveObserverCaptor.getValue().onError(saveError);

        verify(chatMessageGrpcClient).hardDelete(
                anyString(),
                eq(roomId),
                hardDeleteObserverCaptor.capture()
        );

        Throwable hardDeleteError = Status.DEADLINE_EXCEEDED
                .withDescription("hard delete deadline")
                .asRuntimeException();

        // when
        hardDeleteObserverCaptor.getValue().onError(hardDeleteError);

        // then
        verify(grpcMetrics).recordChatMessageHardDeleteError(Status.Code.DEADLINE_EXCEEDED);
    }

    private ChatMessageRequest request() {
        return new ChatMessageRequest(
                clientMessageId,
                roomId,
                writerId,
                content
        );
    }
}
