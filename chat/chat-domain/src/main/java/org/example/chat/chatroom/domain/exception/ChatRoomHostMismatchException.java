package org.example.chat.chatroom.domain.exception;

import org.example.common.exception.ForbiddenException;

public class ChatRoomHostMismatchException extends ForbiddenException {
    public ChatRoomHostMismatchException(String roomId, String myUserId) {
        super(String.format("Not the host of roomId=%s, myUserId=%s", roomId, myUserId));
    }
}
