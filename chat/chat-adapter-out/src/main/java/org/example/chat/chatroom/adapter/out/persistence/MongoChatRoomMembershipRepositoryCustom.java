package org.example.chat.chatroom.adapter.out.persistence;

import java.util.List;
import java.util.Set;

public interface MongoChatRoomMembershipRepositoryCustom {

    List<MongoChatRoomMembership> listLatestActiveMemberships(String memberId, int limit);

    List<MongoChatRoomMembership> listActiveMembershipsBefore(
            String memberId,
            String lastRoomId,
            Long score,
            int limit
    );

    void upsertUnreadActivity(String roomId, Set<String> memberIds, long score);

    void updateScore(String id, long score);
}
