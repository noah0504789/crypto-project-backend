package org.example.websocket.gateway.chatmessage.application.port.out;

import org.example.websocket.gateway.chatmessage.application.service.result.ChatMessageAckResult;

public interface ChatMessageAckPort {

    void success(String userId, ChatMessageAckResult result);

    void failure(String userId, String clientMessageId, String errorCode);
}