package org.example.chat.chatroom.adapter.out.id;

import lombok.RequiredArgsConstructor;
import org.example.chat.chatroom.application.port.out.ChatRoomIdGeneratorPort;
import org.example.common.id.ObjectIdGenerator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ObjectIdChatRoomIdGeneratorAdapter implements ChatRoomIdGeneratorPort {

    private final ObjectIdGenerator objectIdGenerator;

    @Override
    public String generate() {
        return objectIdGenerator.generate();
    }
}