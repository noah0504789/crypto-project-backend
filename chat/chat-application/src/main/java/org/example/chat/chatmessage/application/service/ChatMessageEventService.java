package org.example.chat.chatmessage.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatmessage.domain.event.ChatMessagePersistEvent;
import org.example.chat.chatmessage.domain.port.ChatMessageEventHandler;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageEventService implements ChatMessageEventHandler {

    private final ChatMessagePersistencePort chatMessagePersistencePort;
    private final ChatRoomPersistencePort chatRoomPersistencePort;

    @Retryable(
            value = RuntimeException.class, // TODO: exception 서브타입 지정
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2) // 100ms → 200ms → 400ms
    )
    @Transactional("chatMongoTransactionManager")
    public void handle(ChatMessagePersistEvent event, String txId) {
        ChatMessage domain = ChatMessage.fromPayload(event.getPayload());
        String id = domain.getId();
        String roomId = domain.getRoomId();

        try {
            chatMessagePersistencePort.save(domain);
        } catch (DuplicateKeyException e) {
            log.warn("[chat message] persist event 중복으로 인한 스킵. txId={}, chatMessageId={}", txId, id);
            return;
        }

        chatRoomPersistencePort.incrementMsgCnt(roomId);
        chatRoomPersistencePort.updateMembershipScores(roomId, event.getMemberIds(), domain.toEpochMillis());
    }

    @Recover
    public void recover(RuntimeException e, ChatMessagePersistEvent event, String txId) {
        log.error("❌ MongoDB 실패. chatmessage persist dlq 이벤트 발행: txId={}, error={}", txId, e.getMessage());

        ChatMessage domain = ChatMessage.fromPayload(event.getPayload());

        runRecover("chatmessage persist recover", txId, e, () -> domain.recoverPersist(e.getMessage()), event.getPayload());
    }

    private void runRecover(String context, String txId, RuntimeException original, Runnable recoverAction, Object... details) {
        try {
            recoverAction.run();
        } catch (Exception recoverEx) {
            log.error(
                    "[RECOVER-FALLBACK] {} failed. txId={}, originalError={}, recoverError={}, details={}",
                    context,
                    txId,
                    original.getMessage(),
                    recoverEx.getMessage(),
                    Arrays.toString(details),
                    recoverEx
            );
        }
    }
}
