package org.example.chat.chatroom.application.service.command;

public record ChatRoomActivityCommand(
        String roomId,
        String memberId,
        Long lastMsgSeq,
        Long lastMsgMs
) {
}