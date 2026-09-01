package org.example.chat.chatroom.adapter.out.persistence;

import java.util.List;

public interface MongoChatRoomMembershipRepositoryCustom {

    List<MongoChatRoomMembership> listMemberships(String memberId, int limit);
}
