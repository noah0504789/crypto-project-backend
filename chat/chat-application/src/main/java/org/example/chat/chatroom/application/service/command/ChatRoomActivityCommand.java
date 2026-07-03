package org.example.chat.chatroom.application.service.command;

public record ChatRoomActivityCommand(
        String roomId,
        String memberId,
        Long lastMsgReadSeq,
        Long lastMsgCreatedAtMs
) {
}