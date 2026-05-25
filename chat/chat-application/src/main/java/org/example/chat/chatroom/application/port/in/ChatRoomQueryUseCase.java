package org.example.chat.chatroom.application.port.in;

import org.example.chat.chatroom.application.query.MyChatRoomSummary;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;

import java.util.List;

public interface ChatRoomQueryUseCase {

    ChatRoom findById(String id);

    MyChatRoomSummary findActive(String id, String memberId);

    List<ChatRoom> listMostPopular(ChatRoomCategory category, int limit);

    List<ChatRoom> listNextPopular(ChatRoomCategory category, String lastId, Long lastPopularity, int limit);

    List<MyChatRoomSummary> listLatestActive(String memberId, int limit);

    List<MyChatRoomSummary> listActiveBefore(String memberId, String lastId, Boolean lastUnreadFlag, Long lastMsgCreatedAt, int limit);

    boolean existsByTitle(String title);
}
