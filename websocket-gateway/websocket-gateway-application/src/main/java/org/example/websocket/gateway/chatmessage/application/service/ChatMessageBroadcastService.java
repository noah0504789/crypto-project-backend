package org.example.websocket.gateway.chatmessage.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.websocket.gateway.chatmessage.application.port.in.ChatMessageBroadcastUseCase;
import org.example.websocket.gateway.chatmessage.application.port.out.ChatMessageBroadcastPort;
import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageBroadcastCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageBroadcastService implements ChatMessageBroadcastUseCase {

    @Value("${app.instance-id:unknown}")
    private String instanceId;

    private final ChatMessageBroadcastPort chatMessageBroadcastPort;

    @Override
    public void broadcast(ChatMessageBroadcastCommand command, String txId) {
        boolean sent = chatMessageBroadcastPort.broadcast(command, txId);

        if (sent) {
            log.debug(
                    "[stomp] broadcast sent. txId={}, roomId={}, serverId={}",
                    txId,
                    command.roomId(),
                    instanceId
            );
            return;
        }

        log.debug(
                "STOMP skip. no local member session. txId={}, roomId={}, serverId={}",
                txId,
                command.roomId(),
                instanceId
        );
    }
}