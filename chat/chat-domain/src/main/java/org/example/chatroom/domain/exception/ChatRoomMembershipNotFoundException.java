package org.example.chatroom.domain.exception;

import org.example.common.exception.ResourceNotFoundException;

public class ChatRoomMembershipNotFoundException extends ResourceNotFoundException {
    public ChatRoomMembershipNotFoundException(String roomId, String userId) {
        super(String.format("Membership not found for roomId=%s, userId=%s", roomId, userId));
    }
}
