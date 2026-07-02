package org.example.chat.chatroom.application.port.in;

import org.example.chat.chatroom.application.service.query.GetMyChatRoomQuery;
import org.example.chat.chatroom.application.service.query.ListMyChatRoomsQuery;
import org.example.chat.chatroom.application.service.query.ListPopularChatRoomsQuery;
import org.example.chat.chatroom.application.service.result.MyChatRoomSummary;
import org.example.chat.chatroom.domain.model.ChatRoom;

import java.util.List;

public interface ChatRoomQueryUseCase {

    ChatRoom getRoom(String roomId);

    MyChatRoomSummary getMyRoom(GetMyChatRoomQuery query);

    List<ChatRoom> listPopularRooms(ListPopularChatRoomsQuery query);

    List<MyChatRoomSummary> listMyRooms(ListMyChatRoomsQuery query);

    boolean existsByTitle(String title);
}