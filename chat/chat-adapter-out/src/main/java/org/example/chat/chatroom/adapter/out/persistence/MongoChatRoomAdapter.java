package org.example.chat.chatroom.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.example.chat.chatmessage.adapter.out.persistence.MongoChatMessage;
import org.example.chat.chatmessage.adapter.out.persistence.MongoChatMessageRepository;
import org.example.chat.chatroom.application.service.result.ChatRoomMemberReadState;
import org.example.chat.chatroom.application.service.result.MyChatRoomState;
import org.example.chat.chatroom.application.port.out.ChatRoomPersistencePort;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.chat.chatroom.domain.exception.ChatRoomMembershipNotFoundException;
import org.example.chat.chatroom.application.exception.ChatRoomNotFoundException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MongoChatRoomAdapter implements ChatRoomPersistencePort {

    private final MongoChatRoomRepository chatRoomRepository;
    private final MongoChatRoomMembershipRepository membershipRepository;
    private final MongoChatMessageRepository chatMessageRepository;

    @Override
    public Optional<ChatRoom> findById(String id) {
        return chatRoomRepository.findByIdAndDeletedFalse(new ObjectId(id)).map(MongoChatRoom::toDomain);
    }

    @Override
    public Optional<ChatRoom> findByIdWithLatestMessage(String id) {
        return findByIdWithLatestMessageFromPrimary(id);
    }

    @Override
    public List<ChatRoom> listPopularRooms(ChatRoomCategory category, int limit) {
        List<MongoChatRoom> rooms = chatRoomRepository.listPopularRooms(category, 0, limit);

        return attachLatestMessagesFromPrimary(rooms);
    }

    @Override
    public List<ChatRoom> listPopularRoomsAfter(
            ChatRoomCategory category,
            String lastRoomId,
            Long lastPopularity,
            int limit
    ) {
        List<MongoChatRoom> rooms = chatRoomRepository.listPopularRoomsAfter(category, lastRoomId, lastPopularity, limit);

        return attachLatestMessagesFromSecondary(rooms);
    }

    @Override
    public List<ChatRoom> listRoomsForPopularityRecompute(ChatRoomCategory category) {
        return chatRoomRepository.listAllByCategory(category).stream()
                .map(MongoChatRoom::toDomain)
                .toList();
    }

    @Override
    public void updatePopularities(Map<String, Long> roomIdToPopularity) {
        chatRoomRepository.bulkUpdatePopularity(roomIdToPopularity);
    }

    @Override
    public List<MyChatRoomState> listMyRoomStates(String memberId) {
        List<MongoChatRoomMembership> memberships = membershipRepository.listMemberships(memberId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        Map<ObjectId, MongoChatRoomMembership> membershipByRoomId = memberships.stream()
                .collect(Collectors.toMap(MongoChatRoomMembership::getRoomId, Function.identity()));

        List<MongoChatRoom> rooms = chatRoomRepository.listByIdsAndDeletedFalse(
                memberships.stream().map(MongoChatRoomMembership::getRoomId).toList()
        );

        return attachLatestMessagesFromPrimary(rooms).stream()
                .map(room -> {
                    MongoChatRoomMembership membership = membershipByRoomId.get(new ObjectId(room.getId()));
                    long lastMsgReadSeq = membership.getLastMsgReadSeq() == null
                            ? 0L
                            : membership.getLastMsgReadSeq();

                    return new MyChatRoomState(room, lastMsgReadSeq);
                })
                .toList();
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
    public List<ChatRoomMemberReadState> listMemberReadStates(String id) {
        return membershipRepository.findAllByRoomId(new ObjectId(id))
                .stream()
                .map(membership -> new ChatRoomMemberReadState(
                        membership.getMemberId(),
                        membership.getLastMsgReadSeq() == null ? 0L : membership.getLastMsgReadSeq()
                ))
                .toList();
    }

    @Override
    public ChatRoom save(ChatRoom domain) {
        chatRoomRepository.save(MongoChatRoom.fromDomain(domain));

        return domain;
    }

    @Override
    public ChatRoom updateRoomAndReturn(String id, Map<String, Object> updates) {
        return chatRoomRepository.updateRoomAndReturn(new ObjectId(id), updates)
                .map(MongoChatRoom::toDomain)
                .orElseThrow(() -> new ChatRoomNotFoundException(id));
    }

    @Override
    public long updateMessageState(String id, int count, long lastMessageCreatedAtMs) {
        return chatRoomRepository.updateMessageState(
                        new ObjectId(id),
                        count,
                        Instant.ofEpochMilli(lastMessageCreatedAtMs)
                )
                .map(MongoChatRoom::getLastMsgSeq)
                .orElseThrow(() -> new ChatRoomNotFoundException(id));
    }

    @Override
    public void decrementMessageCount(String id) {
        chatRoomRepository.incrementRoomField(new ObjectId(id), "msgCnt", -1);
    }

    @Override
    public void activateMembership(String id, String memberId, Long lastMsgReadSeq) {
        membershipRepository.save(MongoChatRoomMembership.ofReadActivity(id, memberId, lastMsgReadSeq));
    }

    @Override
    public void joinMembership(String id, String memberId) {
        chatRoomRepository.addMember(new ObjectId(id), memberId);
    }

    @Override
    public void leaveMembership(String id, String memberId) {
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

    private Optional<ChatRoom> findByIdWithLatestMessageFromPrimary(String id) {
        ObjectId roomId = new ObjectId(id);

        return chatRoomRepository.findByIdAndDeletedFalse(roomId)
                .map(room -> chatMessageRepository
                        .findTopByRoomIdAndDeletedFalseOrderByCreatedAtDescIdDesc(roomId)
                        .map(latest -> room.toDomainWithLatest(
                                latest.getId().toHexString(),
                                latest.getContent(),
                                latest.getCreatedAt()
                        ))
                        .orElseGet(room::toDomainWithNoLatestMessage)
                );
    }

    private Optional<ChatRoom> findByIdWithLatestMessageFromSecondary(String id) {
        ObjectId roomId = new ObjectId(id);

        return chatRoomRepository.findByIdAndDeletedFalseFromSecondary(roomId)
                .map(room -> chatMessageRepository
                        .findLatestByRoomIdFromSecondary(roomId)
                        .map(latest -> room.toDomainWithLatest(
                                latest.getId().toHexString(),
                                latest.getContent(),
                                latest.getCreatedAt()
                        ))
                        .orElseGet(room::toDomainWithNoLatestMessage)
                );
    }

    private List<ChatRoom> attachLatestMessages(
            List<MongoChatRoom> rooms,
            Function<List<ObjectId>, List<MongoChatMessage>> latestMessageFinder
    ) {
        if (rooms == null || rooms.isEmpty()) {
            return List.of();
        }

        List<ObjectId> roomIds = rooms.stream()
                .map(MongoChatRoom::getId)
                .toList();

        Map<ObjectId, MongoChatMessage> latestMessageMap = latestMessageFinder.apply(roomIds)
                .stream()
                .collect(Collectors.toMap(
                        MongoChatMessage::getRoomId,
                        Function.identity()
                ));

        return rooms.stream()
                .map(room -> Optional.ofNullable(latestMessageMap.get(room.getId()))
                        .map(latest -> room.toDomainWithLatest(
                                latest.getId().toHexString(),
                                latest.getContent(),
                                latest.getCreatedAt()
                        ))
                        .orElseGet(room::toDomainWithNoLatestMessage)
                )
                .toList();
    }

    private List<ChatRoom> attachLatestMessagesFromPrimary(List<MongoChatRoom> rooms) {
        return attachLatestMessages(rooms, chatMessageRepository::listLatestMessagesByRoomIds);
    }

    private List<ChatRoom> attachLatestMessagesFromSecondary(List<MongoChatRoom> rooms) {
        return attachLatestMessages(rooms, chatMessageRepository::listLatestMessagesByRoomIdsFromSecondary);
    }
}
