package org.example.chat.chatmessage.application.port.in;

import org.example.chat.chatmessage.application.service.query.ListChatMessagesQuery;
import org.example.chat.chatmessage.domain.model.ChatMessage;

import java.util.List;

public interface ChatMessageQueryUseCase {

    List<ChatMessage> listMessages(ListChatMessagesQuery query);
}