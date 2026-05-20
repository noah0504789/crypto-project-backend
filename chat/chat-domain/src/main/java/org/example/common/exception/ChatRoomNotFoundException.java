package org.example.common.exception;

public class ChatRoomNotFoundException extends ResourceNotFoundException {
    public ChatRoomNotFoundException(String id) {
        super("chatroom not found! roomId="+id);
    }
}
