package org.example.chat.chatroom.application.port.in;

public interface ChatRoomActivityProjectionUseCase {

    void flush();

    void reclaimStalled();
}
