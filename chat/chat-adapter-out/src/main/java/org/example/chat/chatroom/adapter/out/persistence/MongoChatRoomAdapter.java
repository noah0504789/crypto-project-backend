package org.example.chat.chatroom.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.example.chat.chatmessage.adapter.out.persistence.MongoChatMessage;
import org.example.chat.chatmessage.adapter.out.persistence.MongoChatMessageRepository;
import org.example.chat.chatroom.application.dto.ChatRoomMembershipScore;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatroom.domain.exception.ChatRoomMembershipNotFoundException;
import org.example.chat.chatroom.domain.exception.ChatRoomNotFoundException;
import org.example.chat.chatroom.domain.service.MyChatRoomScoreCalculator;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MongoChatRoomAdapter implements ChatRoomPersistencePort {

    private final MongoChatRoomRepository chatRoomRepository;
    private final MongoChatRoomMembershipRepository membershipRepository;
    private final MongoChatMessageRepository chatMessageRepository;

    @Override
    public ChatRoom save(ChatRoom domain) {
        chatRoomRepository.save(MongoChatRoom.fromDomain(domain));

        return domain;
    }

    @Override
    public ChatRoom updateAndReturn(String id, Map<String, Object> updated) {
        return chatRoomRepository.updateAndReturn(new ObjectId(id), updated)
                .map(this::toDomain)
                .orElseThrow(() -> new ChatRoomNotFoundException(id));
    }

    @Override
    public void updateMembershipScores(String id, Set<String> memberIds, long lastMsgCreatedAt) {
        long score = MyChatRoomScoreCalculator.unread(lastMsgCreatedAt);

        memberIds.forEach(memberId -> membershipRepository.upsert(MongoChatRoomMembership.ofUnreadActivity(id, memberId, score)));
    }

    @Override
    public List<ChatRoomMembershipScore> refreshMembershipScores(String id, long fallbackMsgCreatedAt) {
        return membershipRepository.findAllByRoomId(new ObjectId(id))
                .stream()
                .map(membership -> {
                    long score = membership.getScore() == null ? 0L : membership.getScore();
                    long newScore = MyChatRoomScoreCalculator.rescoreKeepingUnreadState(score, fallbackMsgCreatedAt);
                    membershipRepository.refresh(membership.getId(), newScore);

                    return new ChatRoomMembershipScore(membership.getMemberId(), newScore);
                })
                .toList();
    }

    @Override
    public void incrementMsgCnt(String id) {
        chatRoomRepository.incrementField(new ObjectId(id), "msgCnt", 1);
    }

    @Override
    public void decrementMsgCnt(String id) {
        chatRoomRepository.incrementField(new ObjectId(id), "msgCnt", -1);
    }

    @Override
    public void active(String id, String memberId, Long lastMsgReadSeq, Long lastMsgCreatedAt) {
        long score = MyChatRoomScoreCalculator.read(lastMsgCreatedAt);

        membershipRepository.save(MongoChatRoomMembership.ofReadActivity(id, memberId, lastMsgReadSeq, score));
    }

    @Override
    public void join(String id, String memberId) {
        chatRoomRepository.addMember(new ObjectId(id), memberId);
    }

    @Override
    public void leave(String id, String memberId) {
        chatRoomRepository.removeMember(new ObjectId(id), memberId);
        membershipRepository.deleteByRoomIdAndMemberId(new ObjectId(id), memberId);
    }

    @Override
    public void deleteById(String id) {
        ObjectId id_ = new ObjectId(id);
        chatRoomRepository.softDeleteById(id_);
        membershipRepository.deleteByRoomId(id_);
        chatMessageRepository.softDeleteByRoomId(id_);
    }

    @Override
    public Optional<ChatRoom> findById(String id) {
        return chatRoomRepository.findByIdAndDeletedFalse(new ObjectId(id)).map(this::toDomain);
    }

    @Override
    public Optional<ChatRoom> findByIdWithLatest(String id) {
        ObjectId roomId = new ObjectId(id);

        return chatRoomRepository.findByIdAndDeletedFalse(roomId)
                .map(room -> {
                    MongoChatMessage latest = chatMessageRepository.findTopByRoomIdAndDeletedFalseOrderByCreatedAtDescIdDesc(roomId)
                            .orElse(null);

                    return toDomainWithLatest(room, latest);
                });
    }

    @Override
    public boolean existsByTitle(String title) {
        return chatRoomRepository.existsByTitleAndDeletedFalse(title);
    }

    @Override
    public Long getLastReadSeq(String id, String memberId) {
        return membershipRepository.findById(MongoChatRoomMembership.generateId(id, memberId))
                .orElseThrow(()-> new ChatRoomMembershipNotFoundException(id, memberId))
                .getLastMsgReadSeq();
    }

    @Override
    public List<ChatRoom> listMostPopular(ChatRoomCategory category, int limit) {
        List<MongoChatRoom> rooms = chatRoomRepository.listMostPopular(category, 0, limit);

        return attachLatestMessages(rooms);
    }

    @Override
    public List<ChatRoom> listNextPopular(ChatRoomCategory category, String lastId, Long lastPopularity, int limit) {
        List<MongoChatRoom> rooms = chatRoomRepository.listNextPopular(category, lastId, lastPopularity, limit);

        return attachLatestMessages(rooms);
    }

    @Override
    public List<ChatRoom> listLatestActive(String memberId, int limit) {
        return membershipRepository.listLatestActive(memberId, limit).stream()
                .map(membership -> membership.getRoomId().toHexString())
                .map(this::findByIdWithLatest)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public List<ChatRoom> listActiveBefore(String memberId, String lastId, Long score, int limit) {
        return membershipRepository.listActiveBefore(memberId, lastId, score, limit).stream()
                .map(membership -> membership.getRoomId().toHexString())
                .map(this::findByIdWithLatest)
                .flatMap(Optional::stream)
                .toList();
    }

    private List<ChatRoom> attachLatestMessages(List<MongoChatRoom> rooms) {
        if (rooms.isEmpty()) return List.of();

        List<ObjectId> roomIds = rooms.stream().map(MongoChatRoom::getId).toList();
        Map<ObjectId, MongoChatMessage> latestMsgMap = chatMessageRepository.findLatestByRoomIds(roomIds).stream()
                .collect(Collectors.toMap(MongoChatMessage::getRoomId, Function.identity()));

        return rooms.stream()
                .map(room -> toDomainWithLatest(room, latestMsgMap.get(room.getId())))
                .toList();
    }

    private ChatRoom toDomain(MongoChatRoom room) {
        return ChatRoom.rehydrate(
                room.getId().toHexString(),
                room.getHostId(),
                room.getTitle(),
                room.getDescription(),
                room.getCategory(),
                room.getMemberIds(),
                room.getMsgCnt(),
                room.getCreatedAt()
        );
    }

    private ChatRoom toDomainWithLatest(MongoChatRoom room, MongoChatMessage latest) {
        return ChatRoom.rehydrateWithLatest(
                room.getId().toHexString(),
                room.getHostId(),
                room.getTitle(),
                room.getDescription(),
                room.getCategory(),
                room.getMemberIds(),
                room.getMsgCnt(),
                latest == null ? "" : latest.getId().toHexString(),
                latest == null ? "" : latest.getContent(),
                latest == null ? null : latest.toInstant(),
                room.getCreatedAt()
        );
    }
}
