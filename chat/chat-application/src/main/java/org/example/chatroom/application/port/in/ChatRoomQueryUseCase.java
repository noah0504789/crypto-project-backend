package org.example.chatroom.application.port.in;

import org.example.chatroom.application.dto.MyChatRoomResponse;
import org.example.chatroom.domain.model.ChatRoom;
import org.example.chatroom.domain.model.ChatRoomCategory;

import java.util.List;

public interface ChatRoomQueryUseCase {

    ChatRoom findById(String id);

    MyChatRoomResponse findActive(String id, String memberId);

    List<ChatRoom> listMostPopular(ChatRoomCategory category, int limit);

    List<ChatRoom> listNextPopular(ChatRoomCategory category, String lastId, Long lastPopularity, int limit);

    List<MyChatRoomResponse> listLatestActive(String memberId, int limit);

    List<MyChatRoomResponse> listActiveBefore(String memberId, String lastId, Boolean lastUnreadFlag, Long lastMsgCreatedAt, int limit);

    boolean existsByTitle(String title);
}
