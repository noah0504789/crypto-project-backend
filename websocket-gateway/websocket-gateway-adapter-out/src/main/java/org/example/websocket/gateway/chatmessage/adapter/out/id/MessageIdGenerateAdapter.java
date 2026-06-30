package org.example.websocket.gateway.chatmessage.adapter.out.id;

import lombok.RequiredArgsConstructor;
import org.example.common.id.ObjectIdGenerator;
import org.example.websocket.gateway.chatmessage.application.port.out.MessageIdGeneratePort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageIdGenerateAdapter implements MessageIdGeneratePort {

    private final ObjectIdGenerator objectIdGenerator;

    @Override
    public String generate() {
        return objectIdGenerator.generate();
    }
}