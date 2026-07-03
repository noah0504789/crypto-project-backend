package org.example.chat.chatroom.adapter.out.persistence;

import java.util.List;

public interface MongoChatRoomMembershipRepositoryCustom {

    void upsert(MongoChatRoomMembership entity);

    List<MongoChatRoomMembership> listLatestActiveMemberships(String memberId, int limit);

    List<MongoChatRoomMembership> listActiveMembershipsBefore(
            String memberId,
            String lastRoomId,
            Long score,
            int limit
    );

    void updateScore(String id, long score);
}
