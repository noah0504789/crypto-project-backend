package org.example.chat.chatmessage.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatmessage.application.event.dlq.ChatMessageDlqEventList;
import org.example.chat.chatmessage.application.event.dlq.ChatMessagePersistDlqEvent;
import org.example.chat.chatmessage.application.mapper.ChatMessagePayloadMapper;
import org.example.chat.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatmessage.application.event.ChatMessagePersistEvent;
import org.example.chat.chatmessage.application.port.in.ChatMessageEventHandler;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatmessage.application.exception.DuplicateChatMessageException;
import org.example.chat.exception.TemporaryChatPersistenceException;
import org.example.common.dlq.application.port.out.DlqEventListPublishPort;
import org.example.contract.chatmessage.ChatMessagePayload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageEventService implements ChatMessageEventHandler {

    private final ChatMessagePersistencePort chatMessagePersistencePort;
    private final ChatRoomPersistencePort chatRoomPersistencePort;
    private final DlqEventListPublishPort dlqEventListPublishPort;

    @Retryable(
            retryFor = TemporaryChatPersistenceException.class,
            noRetryFor = DuplicateChatMessageException.class,
            maxAttemptsExpression = "${chat.retry.max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${chat.retry.backoff-delay-ms:100}",
                    multiplierExpression = "${chat.retry.backoff-multiplier:2}")
    )
    @Transactional("chatMongoTransactionManager")
    public void handle(ChatMessagePersistEvent event, String txId) {
        ChatMessagePayload payload = event.getPayload();
        ChatMessage domain = ChatMessagePayloadMapper.toDomain(payload);
        String id = domain.getId();
        String roomId = domain.getRoomId();

        try {
            chatMessagePersistencePort.save(domain);
        } catch (DuplicateChatMessageException e) {
            log.warn(
                    "[chat message] persist event already processed. txId={}, chatMessageId={}",
                    txId,
                    id
            );
            return;
        }

        chatRoomPersistencePort.incrementMessageCount(roomId);
        chatRoomPersistencePort.updateMembershipScores(roomId, event.getMemberIds(), domain.createdAtEpochMillis());
    }

    @Recover
    public void recover(
            TemporaryChatPersistenceException e,
            ChatMessagePersistEvent event,
            String txId
    ) {
        log.error(
                "[dlq] chat message persist retry exhausted. txId={}",
                txId,
                e
        );

        ChatMessagePayload payload = event.getPayload();
        ChatMessage domain = ChatMessagePayloadMapper.toDomain(payload);

        runRecover(
                txId,
                e,
                () -> publishPersistDlqEvent(domain, e.getMessage()),
                event.getPayload()
        );
    }

    private void runRecover(
            String txId,
            RuntimeException original,
            Runnable recoverAction,
            Object... details
    ) {
        try {
            recoverAction.run();
        } catch (Exception recoverEx) {
            log.error(
                    "[recover-fallback] {} failed. txId={}, originalError={}, recoverError={}, details={}",
                    "chatmessage persist recover",
                    txId,
                    original.getMessage(),
                    recoverEx.getMessage(),
                    Arrays.toString(details),
                    recoverEx
            );
        }
    }

    private void publishPersistDlqEvent(ChatMessage domain, String errorMessage) {
        ChatMessagePayload chatMessagePayload = ChatMessagePayloadMapper.fromDomain(domain);
        ChatMessageDlqEventList chatMessageDlqEventList =
                ChatMessageDlqEventList.of(
                    new ChatMessagePersistDlqEvent(chatMessagePayload, errorMessage)
                );

        dlqEventListPublishPort.publish(chatMessageDlqEventList);
    }
}