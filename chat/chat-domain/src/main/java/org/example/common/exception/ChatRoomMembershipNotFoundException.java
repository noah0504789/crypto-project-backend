package org.example.common.exception;

public class ChatRoomMembershipNotFoundException extends ResourceNotFoundException {
    public ChatRoomMembershipNotFoundException(String roomId, String userId) {
        super(String.format("Membership not found for roomId=%s, userId=%s", roomId, userId));
    }
}
