package org.example.chatroom.adapter.out.persistence;

import java.util.List;

public interface MongoChatRoomMembershipRepositoryCustom {

    void upsert(MongoChatRoomMembership entity);

    List<MongoChatRoomMembership> listLatestActive(String memberId, int limit);

    List<MongoChatRoomMembership> listActiveBefore(String memberId, String lastId, Long score, int limit);

    void refresh(String id, long score);
}
