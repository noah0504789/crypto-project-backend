package org.example.websocket.gateway.chatmessage.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.grpc.exception.GrpcClientException;
import org.example.common.grpc.exception.GrpcFailureCode;
import org.example.websocket.gateway.chatmessage.application.port.in.ChatMessageSendUseCase;
import org.example.websocket.gateway.chatmessage.application.port.out.ChatMessageAckPort;
import org.example.websocket.gateway.chatmessage.application.port.out.ChatMessageCommandPort;
import org.example.websocket.gateway.chatmessage.application.port.out.ChatMessageMetricsPort;
import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageHardDeleteCommand;
import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageSendCommand;
import org.example.websocket.gateway.chatmessage.application.service.result.ChatMessageAckResult;
import org.example.websocket.gateway.chatmessage.application.service.result.ChatMessageSendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageSendService implements ChatMessageSendUseCase {

    private final ChatMessageCommandPort chatMessageCommandPort;
    private final ChatMessageAckPort chatMessageAckPort;
    private final ChatMessageMetricsPort chatMessageMetricsPort;
    private final Executor chatMessageAckExecutor;

    // 콜백 실행 스레드를 명시한다. 생략하면 gRPC 응답 스레드(상한 없는 캐시 풀)에서 돈다.
    // 성공·실패를 한 분기에서 받는다. thenAccept 뒤에 exceptionally 를 붙이면 성공 ACK 전송 실패가
    // 저장 실패로 섞여, 저장에 성공한 메시지에 실패 ACK 가 나간다(클라이언트 재전송 유발).
    @Override
    public void send(ChatMessageSendCommand command) {
        chatMessageCommandPort.save(command)
                .whenCompleteAsync((result, error) -> {
                    if (error != null) {
                        handleSaveError(command, error);
                    } else {
                        sendSuccessAck(command, result);
                    }
                }, chatMessageAckExecutor);
    }

    private void sendSuccessAck(ChatMessageSendCommand command, ChatMessageSendResult result) {
        chatMessageAckPort.success(
                command.writerId(),
                ChatMessageAckResult.from(command, result)
        );
    }

    private void handleSaveError(ChatMessageSendCommand command, Throwable error) {
        GrpcClientException resolved = GrpcClientException.resolve(error);
        GrpcFailureCode code = resolved.getCode();

        if (code.isRecordable()) {
            chatMessageMetricsPort.recordSaveFailure(code);
        }

        if (shouldHardDelete(code)) {
            hardDelete(command);
        }

        chatMessageAckPort.failure(
                command.writerId(),
                command.clientMessageId(),
                code.name()
        );
    }

    private boolean shouldHardDelete(GrpcFailureCode code) {
        return code == GrpcFailureCode.DEADLINE_EXCEEDED;
    }

    private void hardDelete(ChatMessageSendCommand command) {
        chatMessageCommandPort.hardDelete(
                ChatMessageHardDeleteCommand.dueToSaveTimeout(command)
        ).exceptionally(error -> {
            GrpcClientException resolved = GrpcClientException.resolve(error);
            GrpcFailureCode code = resolved.getCode();

            if (code.isRecordable()) {
                chatMessageMetricsPort.recordHardDeleteFailure(code);
            }

            log.warn(
                    "[grpc] chat body hardDelete failed. messageId={}, roomId={}, code={}",
                    command.messageId(),
                    command.roomId(),
                    code,
                    error
            );

            return null;
        });
    }
}
