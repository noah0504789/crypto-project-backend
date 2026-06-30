package org.example.chat.chatmessage.application.port.in;

import org.example.chat.chatmessage.application.dto.ChatMessageSaveCommand;
import org.example.chat.chatmessage.application.dto.ChatMessageSaveResult;

public interface ChatMessageCommandUseCase {

    ChatMessageSaveResult save(ChatMessageSaveCommand command);

    void hardDelete(String messageId, String roomId);
}