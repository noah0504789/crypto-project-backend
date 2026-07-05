package org.example.websocket.gateway.chatmessage.application.service;

import org.example.common.grpc.exception.GrpcClientException;
import org.example.common.grpc.exception.GrpcFailureCode;
import org.example.websocket.gateway.chatmessage.application.port.out.ChatMessageAckPort;
import org.example.websocket.gateway.chatmessage.application.port.out.ChatMessageCommandPort;
import org.example.websocket.gateway.chatmessage.application.port.out.ChatMessageMetricsPort;
import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageHardDeleteCommand;
import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageSendCommand;
import org.example.websocket.gateway.chatmessage.application.service.result.ChatMessageAckResult;
import org.example.websocket.gateway.chatmessage.application.service.result.ChatMessageHardDeleteResult;
import org.example.websocket.gateway.chatmessage.application.service.result.ChatMessageSendResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageSendServiceTest {

    @Mock
    private ChatMessageCommandPort chatMessageCommandPort;

    @Mock
    private ChatMessageAckPort chatMessageAckPort;

    @Mock
    private ChatMessageMetricsPort chatMessageMetricsPort;

    @InjectMocks
    private ChatMessageSendService sut;

    @Test
    @DisplayName("save 성공 시 writer에게 성공 ACK를 전송한다")
    void send_shouldSendSuccessAck_whenSaveSucceeded() {
        ChatMessageSendCommand command = command();

        ChatMessageSendResult result = new ChatMessageSendResult(
                true,
                "body-id",
                12345L
        );

        when(chatMessageCommandPort.save(command))
                .thenReturn(CompletableFuture.completedFuture(result));

        sut.send(command);

        ArgumentCaptor<ChatMessageAckResult> ackCaptor =
                ArgumentCaptor.forClass(ChatMessageAckResult.class);

        verify(chatMessageAckPort).success(eq("writer-id"), ackCaptor.capture());

        ChatMessageAckResult ack = ackCaptor.getValue();

        assertThat(ack.messageId()).isEqualTo("body-id");
        assertThat(ack.clientMessageId()).isEqualTo("client-body-id");
        assertThat(ack.success()).isTrue();
        assertThat(ack.timestamp()).isEqualTo(12345L);

        verify(chatMessageAckPort, never()).failure(any(), any(), any());
        verify(chatMessageMetricsPort, never()).recordSaveFailure(any());
        verify(chatMessageCommandPort, never()).hardDelete(any());
    }

    @Test
    @DisplayName("save 중 DEADLINE_EXCEEDED 발생 시 save metric 기록, hardDelete 실행, 실패 ACK를 전송한다")
    void send_shouldRecordMetricHardDeleteAndSendFailureAck_whenSaveDeadlineExceeded() {
        ChatMessageSendCommand command = command();

        when(chatMessageCommandPort.save(command))
                .thenReturn(failedFuture(grpcException(GrpcFailureCode.DEADLINE_EXCEEDED)));

        when(chatMessageCommandPort.hardDelete(any()))
                .thenReturn(CompletableFuture.completedFuture(hardDeleteResult()));

        sut.send(command);

        verify(chatMessageMetricsPort).recordSaveFailure(GrpcFailureCode.DEADLINE_EXCEEDED);

        ArgumentCaptor<ChatMessageHardDeleteCommand> hardDeleteCaptor =
                ArgumentCaptor.forClass(ChatMessageHardDeleteCommand.class);

        verify(chatMessageCommandPort).hardDelete(hardDeleteCaptor.capture());

        ChatMessageHardDeleteCommand hardDeleteCommand = hardDeleteCaptor.getValue();

        assertThat(hardDeleteCommand.messageId()).isEqualTo("body-id");
        assertThat(hardDeleteCommand.roomId()).isEqualTo("room-id");

        verify(chatMessageAckPort).failure(
                "writer-id",
                "client-body-id",
                GrpcFailureCode.DEADLINE_EXCEEDED.name()
        );

        verify(chatMessageAckPort, never()).success(any(), any());
    }

    @Test
    @DisplayName("save 중 CANCELLED 발생 시 save metric 기록, hardDelete 없이 실패 ACK를 전송한다")
    void send_shouldRecordMetricAndSendFailureAck_whenSaveCancelled() {
        ChatMessageSendCommand command = command();

        when(chatMessageCommandPort.save(command))
                .thenReturn(failedFuture(grpcException(GrpcFailureCode.CANCELLED)));

        sut.send(command);

        verify(chatMessageMetricsPort).recordSaveFailure(GrpcFailureCode.CANCELLED);

        verify(chatMessageCommandPort, never()).hardDelete(any());

        verify(chatMessageAckPort).failure(
                "writer-id",
                "client-body-id",
                GrpcFailureCode.CANCELLED.name()
        );

        verify(chatMessageAckPort, never()).success(any(), any());
    }

    @Test
    @DisplayName("save 중 UNKNOWN 발생 시 metric과 hardDelete 없이 실패 ACK를 전송한다")
    void send_shouldOnlySendFailureAck_whenSaveUnknownFailed() {
        ChatMessageSendCommand command = command();

        when(chatMessageCommandPort.save(command))
                .thenReturn(failedFuture(grpcException(GrpcFailureCode.UNKNOWN)));

        sut.send(command);

        verify(chatMessageMetricsPort, never()).recordSaveFailure(any());
        verify(chatMessageCommandPort, never()).hardDelete(any());

        verify(chatMessageAckPort).failure(
                "writer-id",
                "client-body-id",
                GrpcFailureCode.UNKNOWN.name()
        );

        verify(chatMessageAckPort, never()).success(any(), any());
    }

    @Test
    @DisplayName("DEADLINE_EXCEEDED 보상 hardDelete 중 CANCELLED 발생 시 hardDelete metric을 기록한다")
    void send_shouldRecordHardDeleteMetric_whenHardDeleteCancelled() {
        ChatMessageSendCommand command = command();

        when(chatMessageCommandPort.save(command))
                .thenReturn(failedFuture(grpcException(GrpcFailureCode.DEADLINE_EXCEEDED)));

        when(chatMessageCommandPort.hardDelete(any()))
                .thenReturn(failedFuture(grpcException(GrpcFailureCode.CANCELLED)));

        sut.send(command);

        verify(chatMessageMetricsPort).recordSaveFailure(GrpcFailureCode.DEADLINE_EXCEEDED);
        verify(chatMessageMetricsPort).recordHardDeleteFailure(GrpcFailureCode.CANCELLED);

        verify(chatMessageAckPort).failure(
                "writer-id",
                "client-body-id",
                GrpcFailureCode.DEADLINE_EXCEEDED.name()
        );
    }

    private ChatMessageSendCommand command() {
        return new ChatMessageSendCommand(
                "client-body-id",
                "body-id",
                "room-id",
                "writer-id",
                "hello"
        );
    }

    private ChatMessageHardDeleteResult hardDeleteResult() {
        return new ChatMessageHardDeleteResult(
                true,
                "body-id",
                true,
                false,
                false
        );
    }

    private GrpcClientException grpcException(GrpcFailureCode code) {
        return new GrpcClientException(
                code,
                code.name(),
                new RuntimeException(code.name())
        );
    }

    private <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }
}