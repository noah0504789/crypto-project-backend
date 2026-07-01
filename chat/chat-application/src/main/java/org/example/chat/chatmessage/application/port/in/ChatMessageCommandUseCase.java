package org.example.chat.chatmessage.application.port.in;

import org.example.chat.chatmessage.application.service.command.ChatMessageSaveCommand;
import org.example.chat.chatmessage.application.service.result.ChatMessageSaveResult;

public interface ChatMessageCommandUseCase {

    ChatMessageSaveResult save(ChatMessageSaveCommand command);

    void hardDelete(String messageId, String roomId);
}