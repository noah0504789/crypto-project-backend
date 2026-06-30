package org.example.websocket.gateway.chatmessage.application.port.out;

import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageHardDeleteCommand;
import org.example.websocket.gateway.chatmessage.application.service.result.ChatMessageHardDeleteResult;
import org.example.websocket.gateway.chatmessage.application.service.command.ChatMessageSendCommand;
import org.example.websocket.gateway.chatmessage.application.service.result.ChatMessageSendResult;

import java.util.concurrent.CompletableFuture;

public interface ChatMessageCommandPort {

    CompletableFuture<ChatMessageSendResult> save(ChatMessageSendCommand command);

    CompletableFuture<ChatMessageHardDeleteResult> hardDelete(ChatMessageHardDeleteCommand command);
}