package org.example.websocket.gateway.chatroom.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.websocket.gateway.chatroom.application.port.in.MyChatRoomBadgeSendUseCase;
import org.example.websocket.gateway.chatroom.application.port.out.MyChatRoomBadgePort;
import org.example.websocket.gateway.chatroom.application.service.command.MyChatRoomBadgeCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MyChatRoomBadgeSendService implements MyChatRoomBadgeSendUseCase {

    @Value("${app.instance-id:unknown}")
    private String instanceId;

    private final MyChatRoomBadgePort myChatRoomBadgePort;

    @Override
    public void send(MyChatRoomBadgeCommand command, String txId) {
        boolean sent = myChatRoomBadgePort.send(command, txId);

        if (sent) {
            log.debug("[stomp] badge sent. txId={}, serverId={}", txId, instanceId);
            return;
        }

        log.debug("[stomp] badge skipped (no targets). txId={}, serverId={}", txId, instanceId);
    }
}