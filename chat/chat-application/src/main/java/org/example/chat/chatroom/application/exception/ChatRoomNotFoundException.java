package org.example.chat.chatroom.application.exception;

import org.example.common.exception.ResourceNotFoundException;

public class ChatRoomNotFoundException extends ResourceNotFoundException {
    public ChatRoomNotFoundException(String id) {
        super("chatroom not found! roomId="+id);
    }
}
