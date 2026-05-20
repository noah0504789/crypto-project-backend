package org.example.chatmessage.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.example.chatmessage.application.port.out.ChatMessagePersistencePort;
import org.example.chatmessage.domain.model.ChatMessage;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MongoChatMessageAdapter implements ChatMessagePersistencePort {

    private final MongoChatMessageRepository repository;

    @Override
    public List<ChatMessage> listLatest(String roomId, int limit) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "_id"));
        Pageable pageable = PageRequest.of(0, limit, sort);

        return repository.findByRoomIdAndDeletedFalse(new ObjectId(roomId), pageable).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<ChatMessage> listPrev(String roomId, String lastId, Long lastCreatedAtMillis, int limit) {
        return repository.listPrev(new ObjectId(roomId), new ObjectId(lastId), Instant.ofEpochMilli(lastCreatedAtMillis), limit).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public ChatMessage save(ChatMessage domain) {
        repository.save(MongoChatMessage.fromDomain(domain));

        return domain;
    }

    @Override
    public boolean hardDelete(String id) {
        return repository.hardDelete(new ObjectId(id));
    }

    @Override
    public Optional<ChatMessage> findLatestExcluding(String roomId, String id) {
        return repository.findLatestExcluding(roomId, id).map(this::toDomain);
    }

    private ChatMessage toDomain(MongoChatMessage mongo) {
        return ChatMessage.rehydrate(
                mongo.getId().toHexString(),
                mongo.getRoomId().toHexString(),
                mongo.getWriterId(),
                mongo.getContent(),
                mongo.getCreatedAt()
        );
    }
}
