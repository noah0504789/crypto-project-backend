package org.example.websocket.gateway.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.websocket.gateway.chatroom.application.port.in.MyChatRoomBadgeEventHandler;
import org.example.websocket.gateway.chatroom.application.port.out.MyChatRoomBadgePort;
import org.example.websocket.gateway.chatroom.application.service.command.MyChatRoomBadgeCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MyChatRoomBadgeEventService implements MyChatRoomBadgeEventHandler {

    @Value("${app.instance-id:unknown}")
    private String instanceId;

    private final MyChatRoomBadgePort myChatRoomBadgePort;

    @Override
    public void handle(MyChatRoomBadgeCommand command, String txId) {
        boolean sent = myChatRoomBadgePort.send(command, txId);

        if (sent) {
            log.debug("✅ STOMP 처리 완료: txId={}, serverId={}", txId, instanceId);
            return;
        }

        log.debug("STOMP 전체 skip: txId={}, serverId={}", txId, instanceId);
    }
}