package org.example.chat.chatmessage.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.example.chat.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.chat.exception.InvalidResourceRequestException;
import org.example.chat.infra.exception.MongoChatPersistenceExceptionTranslator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@Repository
@RequiredArgsConstructor
public class MongoChatMessageAdapter implements ChatMessagePersistencePort {

    private final MongoChatMessageRepository repository;

    @Override
    public List<ChatMessage> listLatestMessages(String roomId, int limit) {
        return execute(
                "failed to list latest chat messages. roomId=" + roomId,
                () -> {
                    Sort sort = Sort.by(Sort.Direction.DESC, "createdAt")
                            .and(Sort.by(Sort.Direction.DESC, "_id"));

                    Pageable pageable = PageRequest.of(0, limit, sort);

                    return repository.findByRoomIdAndDeletedFalse(
                                    objectId(roomId, "roomId"),
                                    pageable
                            )
                            .stream()
                            .map(MongoChatMessage::toDomain)
                            .toList();
                }
        );
    }

    @Override
    public List<ChatMessage> listMessagesBefore(
            String roomId,
            String lastMsgId,
            Long lastCreatedAtMs,
            int limit
    ) {
        return execute(
                "failed to list previous chat messages. roomId=" + roomId + ", lastMsgId=" + lastMsgId,
                () -> repository.listMessagesBefore(
                                objectId(roomId, "roomId"),
                                objectId(lastMsgId, "lastMsgId"),
                                Instant.ofEpochMilli(lastCreatedAtMs),
                                limit
                        )
                        .stream()
                        .map(MongoChatMessage::toDomain)
                        .toList()
        );
    }

    @Override
    public ChatMessage save(ChatMessage domain) {
        try {
            repository.insert(MongoChatMessage.fromDomain(domain));
            return domain;
        } catch (Exception e) {
            throw MongoChatPersistenceExceptionTranslator.translateChatMessageSave(
                    domain,
            "failed to save chat message. messageId=" + domain.getId() + ", roomId=" + domain.getRoomId(),
                    e
            );
        }
    }

    @Override
    public Set<String> saveAll(Set<ChatMessage> domains) {
        if (domains == null || domains.isEmpty()) {
            return Set.of();
        }

        try {
            Set<ObjectId> ids = Set.copyOf(domains.stream()
                    .map(domain -> objectId(domain.getId(), "messageId"))
                    .toList());
            Set<ObjectId> existingIds = repository.findExistingIds(ids);
            List<MongoChatMessage> newMessages = domains.stream()
                    .map(MongoChatMessage::fromDomain)
                    .filter(message -> !existingIds.contains(message.getId()))
                    .toList();

            if (!newMessages.isEmpty()) {
                repository.insert(newMessages);
            }

            return Set.copyOf(newMessages.stream()
                    .map(message -> message.getId().toHexString())
                    .toList());
        } catch (InvalidResourceRequestException e) {
            throw e;
        } catch (Exception e) {
            throw MongoChatPersistenceExceptionTranslator.translate(
                    "failed to save chat messages in batch. size=" + domains.size(),
                    e
            );
        }
    }

    @Override
    public boolean hardDeleteById(String id) {
        return execute(
                "failed to hard delete chat message. messageId=" + id,
                () -> repository.hardDeleteById(objectId(id, "messageId"))
        );
    }

    @Override
    public Optional<ChatMessage> findLatestMessageExcluding(String roomId, String id) {
        return execute(
                "failed to find latest excluding chat message. roomId=" + roomId + ", messageId=" + id,
                () -> repository.findLatestMessageExcluding(roomId, id)
                        .map(MongoChatMessage::toDomain)
        );
    }

    private ObjectId objectId(String value, String fieldName) {
        try {
            return new ObjectId(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidResourceRequestException("invalid ObjectId. field=" + fieldName + ", value=" + value);
        }
    }

    private <T> T execute(String message, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (InvalidResourceRequestException e) {
            throw e;
        } catch (Exception e) {
            throw MongoChatPersistenceExceptionTranslator.translate(message, e);
        }
    }
}
