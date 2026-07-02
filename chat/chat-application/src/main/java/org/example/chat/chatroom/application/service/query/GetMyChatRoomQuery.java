package org.example.chat.chatroom.application.service.query;

public record GetMyChatRoomQuery(
        String roomId,
        String memberId
) {

    public static GetMyChatRoomQuery of(String roomId, String memberId) {
        return new GetMyChatRoomQuery(roomId, memberId);
    }
}