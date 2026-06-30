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

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageSendService implements ChatMessageSendUseCase {

    private final ChatMessageCommandPort chatMessageCommandPort;
    private final ChatMessageAckPort chatMessageAckPort;
    private final ChatMessageMetricsPort chatMessageMetricsPort;

    @Override
    public void send(ChatMessageSendCommand command) {
        chatMessageCommandPort.save(command)
                .thenAccept(result -> sendSuccessAck(command, result))
                .exceptionally(error -> {
                    handleSaveError(command, error);
                    return null;
                });
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
                    "chat message hardDelete failed. messageId={}, roomId={}, code={}",
                    command.messageId(),
                    command.roomId(),
                    code,
                    error
            );

            return null;
        });
    }
}