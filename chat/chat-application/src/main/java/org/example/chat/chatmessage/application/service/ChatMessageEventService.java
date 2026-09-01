package org.example.chat.chatmessage.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chat.chatmessage.application.event.dlq.ChatMessageDlqEventList;
import org.example.chat.chatmessage.application.event.dlq.ChatMessagePersistDlqEvent;
import org.example.chat.chatmessage.application.mapper.ChatMessagePayloadMapper;
import org.example.chat.chatmessage.application.port.out.ChatMessageMetricsPort;
import org.example.chat.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chat.chatmessage.application.exception.DuplicateChatMessageException;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.chatmessage.application.event.ChatMessagePersistEvent;
import org.example.chat.chatmessage.application.port.in.ChatMessageEventHandler;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.exception.TemporaryChatPersistenceException;
import org.example.common.dlq.application.port.out.DlqEventListPublishPort;
import org.example.contract.chatmessage.ChatMessagePayload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageEventService implements ChatMessageEventHandler {

    private final ChatMessagePersistencePort chatMessagePersistencePort;
    private final ChatRoomPersistencePort chatRoomPersistencePort;
    private final DlqEventListPublishPort dlqEventListPublishPort;
    private final ChatMessageMetricsPort metrics;

    @Retryable(
            retryFor = TemporaryChatPersistenceException.class,
            noRetryFor = DuplicateChatMessageException.class,
            maxAttemptsExpression = "${chat.retry.max-attempts}",
            backoff = @Backoff(
                    delayExpression = "${chat.retry.backoff-delay-ms}",
                    multiplierExpression = "${chat.retry.backoff-multiplier}")
    )
    @Transactional("chatMongoTransactionManager")
    public void handle(ChatMessagePersistEvent event, String txId) {
        try {
            handlePersistence(event, txId);
        } catch (TemporaryChatPersistenceException e) {
            metrics.recordRetryableFailure();
            throw e;
        }
    }

    @Override
    @Retryable(
            retryFor = TemporaryChatPersistenceException.class,
            maxAttemptsExpression = "${chat.retry.max-attempts}",
            backoff = @Backoff(
                    delayExpression = "${chat.retry.backoff-delay-ms}",
                    multiplierExpression = "${chat.retry.backoff-multiplier}")
    )
    @Transactional("chatMongoTransactionManager")
    public void handleBatch(List<ChatMessagePersistEvent> events, String txId) {
        try {
            handlePersistenceBatch(events);
        } catch (TemporaryChatPersistenceException e) {
            metrics.recordRetryableFailure();
            throw e;
        }
    }

    private void handlePersistenceBatch(List<ChatMessagePersistEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        Map<String, ChatMessagePersistEvent> eventByMessageId = new LinkedHashMap<>();
        for (ChatMessagePersistEvent event : events) {
            eventByMessageId.putIfAbsent(event.getPayload().id(), event);
        }

        Map<String, ChatMessage> domainByMessageId = eventByMessageId.values().stream()
                .map(event -> ChatMessagePayloadMapper.toDomain(event.getPayload()))
                .collect(Collectors.toMap(ChatMessage::getId, domain -> domain));
        Set<String> insertedIds = new HashSet<>();
        metrics.recordMessageInsert(() -> insertedIds.addAll(
                chatMessagePersistencePort.saveAll(List.copyOf(domainByMessageId.values()))
        ));

        long duplicateCount = events.size() - insertedIds.size();
        for (long i = 0; i < duplicateCount; i++) {
            metrics.recordDuplicateMessage();
        }

        Map<String, List<String>> insertedIdsByRoom = new LinkedHashMap<>();
        Map<String, ChatMessagePersistEvent> latestEventByRoom = new LinkedHashMap<>();
        for (String insertedId : insertedIds) {
            ChatMessagePersistEvent event = eventByMessageId.get(insertedId);
            ChatMessage domain = domainByMessageId.get(insertedId);
            insertedIdsByRoom.computeIfAbsent(domain.getRoomId(), ignored -> new ArrayList<>())
                    .add(insertedId);
            latestEventByRoom.merge(
                    domain.getRoomId(),
                    event,
                    (left, right) -> Comparator.comparing((ChatMessagePersistEvent value) -> value.getPayload().createdAt())
                            .thenComparing(value -> value.getPayload().id())
                            .compare(left, right) >= 0 ? left : right
            );
        }

        insertedIdsByRoom.forEach((roomId, messageIds) -> metrics.recordRoomCounter(
                () -> chatRoomPersistencePort.incrementMessageCount(roomId, messageIds.size())
        ));
        latestEventByRoom.forEach((roomId, event) -> {
            ChatMessage domain = domainByMessageId.get(event.getPayload().id());
            metrics.recordMembership(
                    () -> chatRoomPersistencePort.updateMembershipScores(
                            roomId,
                            event.getMemberIds(),
                            domain.createdAtEpochMillis()
                    )
            );
        });
        metrics.recordCommittedBatch(insertedIds.size(), insertedIdsByRoom.size(), latestEventByRoom.values().stream()
                .mapToInt(event -> event.getMemberIds().size())
                .sum());
    }

    private void handlePersistence(ChatMessagePersistEvent event, String txId) {
        ChatMessage domain = ChatMessagePayloadMapper.toDomain(event.getPayload());
        String id = domain.getId();
        String roomId = domain.getRoomId();

        try {
            metrics.recordMessageInsert(() -> chatMessagePersistencePort.save(domain));
        } catch (DuplicateChatMessageException e) {
            metrics.recordDuplicateMessage();
            log.warn(
                    "[chat message] persist event already processed. txId={}, chatMessageId={}",
                    txId,
                    id
            );
            return;
        }

        metrics.recordRoomCounter(() -> chatRoomPersistencePort.incrementMessageCount(roomId, 1));
        metrics.recordMembership(
                () -> chatRoomPersistencePort.updateMembershipScores(
                        roomId,
                        event.getMemberIds(),
                        domain.createdAtEpochMillis()
                )
        );
        metrics.recordCommittedBatch(1, 1, event.getMemberIds().size());
    }

    public void recover(
            TemporaryChatPersistenceException e,
            ChatMessagePersistEvent event,
            String txId
    ) {
        recover(e, List.of(event), txId);
    }

    @Recover
    public void recover(
            TemporaryChatPersistenceException e,
            List<ChatMessagePersistEvent> events,
            String txId
    ) {
        log.error(
                "[dlq] chat message persist batch retry exhausted. txId={}, size={}",
                txId,
                events.size(),
                e
        );

        events.forEach(event -> {
            ChatMessagePayload payload = event.getPayload();
            ChatMessage domain = ChatMessagePayloadMapper.toDomain(payload);

            runRecover(
                    txId,
                    e,
                    () -> publishPersistDlqEvent(domain, event.getMemberIds(), e.getMessage()),
                    event.getPayload()
            );
        });
    }

    private void runRecover(
            String txId,
            RuntimeException original,
            Runnable recoverAction,
            Object... details
    ) {
        try {
            recoverAction.run();
            metrics.recordDlqPublished();
        } catch (Exception recoverEx) {
            metrics.recordDlqPublishFailed();
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

    private void publishPersistDlqEvent(ChatMessage domain, Set<String> memberIds, String errorMessage) {
        ChatMessagePayload chatMessagePayload = ChatMessagePayloadMapper.fromDomain(domain);
        ChatMessageDlqEventList chatMessageDlqEventList =
                ChatMessageDlqEventList.of(
                    new ChatMessagePersistDlqEvent(chatMessagePayload, memberIds, errorMessage)
                );

        dlqEventListPublishPort.publish(chatMessageDlqEventList);
    }
}
