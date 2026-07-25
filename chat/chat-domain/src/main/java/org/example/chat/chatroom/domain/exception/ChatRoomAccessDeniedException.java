package org.example.chat.chatroom.domain.exception;

import org.example.common.exception.ForbiddenException;

public class ChatRoomAccessDeniedException extends ForbiddenException {
    public ChatRoomAccessDeniedException(String roomId, String myUserId) {
        super(String.format("Not a member of roomId=%s, myUserId=%s", roomId, myUserId));
    }
}
