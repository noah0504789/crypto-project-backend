package org.example.websocket.adapter.in.stomp;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.example.chatmessage.application.port.out.ChatMessageGatewayClient;
import org.example.websocket.stomp.dto.ChatMessageAck;
import org.example.websocket.stomp.dto.ChatMessageRequest;
import org.example.grpc.chatmessage.ChatMessageGrpcResponse;
import org.example.grpc.chatmessage.ChatMessageHardDeleteGrpcResponse;
import org.example.monitoring.application.port.out.GrpcMetricsRecorder;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Set;

@Slf4j
@Controller
@RequiredArgsConstructor
public class StompController {

    private final String CHAT_ACK_DESTINATION = "/queue/chat/ack"; // TODO: 주입
    private final Validator validator;
    private final SimpMessagingTemplate stompTemplate;
    private final ChatMessageGatewayClient chatMessageGrpcClient;
    private final GrpcMetricsRecorder grpcMetrics;

    @MessageMapping("/chat.send")
    public void chatMessage(@Payload @Valid ChatMessageRequest request) {
        String userId = request.writerId();

        Set<ConstraintViolation<ChatMessageRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            stompTemplate.convertAndSendToUser(
                    userId,
                    CHAT_ACK_DESTINATION,
                    ChatMessageAck.ofFailure(request.clientMessageId(), "VALIDATION_FAILED")
            );
            return;
        }

        String messageId = new ObjectId().toHexString();

        chatMessageGrpcClient.save(request, messageId, saveObserver(request, userId, messageId));
    }


    private StreamObserver<ChatMessageGrpcResponse> saveObserver(ChatMessageRequest request, String userId, String messageId) {
        return new StreamObserver<>() {
            @Override
            public void onNext(ChatMessageGrpcResponse response) {
                log.info("grpc callback thread={}, clientMessageId={}, messageId={}",
                        Thread.currentThread().getName(),
                        request.clientMessageId(),
                        messageId);

                stompTemplate.convertAndSendToUser(
                        userId,
                        CHAT_ACK_DESTINATION,
                        ChatMessageAck.ofSuccess(
                                response.getId(),
                                request.clientMessageId(),
                                response.getSuccess(),
                                response.getTs()
                        )
                );
            }

            @Override
            public void onError(Throwable t) {
                handleSaveError(request, userId, messageId, t);
            }

            @Override
            public void onCompleted() {
                // no-op
            }
        };
    }

    private StreamObserver<ChatMessageHardDeleteGrpcResponse> hardDeleteObserver(String messageId, String roomId) {
        return new StreamObserver<>() {
            @Override
            public void onNext(ChatMessageHardDeleteGrpcResponse response) {
                log.info("grpc hardDelete callback success. messageId={}, roomId={}, success={}", messageId, roomId, response.getSuccess());
            }

            @Override
            public void onError(Throwable hardDeleteError) {
                Status.Code grpcCode = Status.fromThrowable(hardDeleteError).getCode();

                log.warn("grpc hardDelete failed. messageId={}, roomId={}, grpcCode={}", messageId, roomId, grpcCode, hardDeleteError);

                if (isCanceledOrDeadlineExceeded(grpcCode)) {
                    grpcMetrics.recordChatMessageHardDeleteError(grpcCode);
                }
            }

            @Override
            public void onCompleted() {
                log.info("grpc hardDelete completed. messageId={}, roomId={}", messageId, roomId);
            }
        };
    }

    private void handleSaveError(ChatMessageRequest request, String userId, String messageId, Throwable t) {
        Status.Code grpcCode = Status.fromThrowable(t).getCode();

        log.warn("grpc save failed. clientMessageId={}, messageId={}, grpcCode={}, thread={}", request.clientMessageId(), messageId, grpcCode, Thread.currentThread().getName(), t);

        if (isCanceledOrDeadlineExceeded(grpcCode)) {
            grpcMetrics.recordChatMessageSaveError(grpcCode);
        }

        String code = resolveErrorCode(grpcCode);

        if (shouldHardDelete(grpcCode)) {
            chatMessageGrpcClient.hardDelete(messageId, request.roomId(), hardDeleteObserver(messageId, request.roomId()));
        }

        stompTemplate.convertAndSendToUser(userId, CHAT_ACK_DESTINATION, ChatMessageAck.ofFailure(request.clientMessageId(), code));
    }

    private boolean shouldHardDelete(Status.Code code) {
        return code == Status.Code.DEADLINE_EXCEEDED;
    }

    private boolean isCanceledOrDeadlineExceeded(Status.Code code) {
        return code == Status.Code.CANCELLED || code == Status.Code.DEADLINE_EXCEEDED;
    }

    private String resolveErrorCode(Status.Code code) {
        return switch (code) {
            case DEADLINE_EXCEEDED -> "DEADLINE_EXCEEDED";
            case CANCELLED -> "CANCELLED";
            default -> "SAVE_FAILED";
        };
    }
}
