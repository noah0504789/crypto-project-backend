package org.example.chat.chatmessage.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.example.chat.chatmessage.adapter.in.web.dto.ChatMessageResponse;
import org.example.chat.chatmessage.application.service.query.ListChatMessagesQuery;
import org.example.common.dto.CursorPage;
import org.example.chat.chatmessage.adapter.in.web.dto.ChatMessageCursor;
import org.example.chat.chatmessage.application.port.in.ChatMessageQueryUseCase;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.example.common.dto.CursorPages;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("${api-path.chat.base:/chat}")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageQueryUseCase chatMessageQueryUseCase;

    @GetMapping("${api-path.chat.room-messages:/room/{roomId}/messages}")
    public ResponseEntity<CursorPage<ChatMessageResponse>> cursorRecentChatMessages(
            @PathVariable("roomId") String roomId,
            @ModelAttribute ChatMessageCursor cursor,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit
    ) {
        int limitPlus1 = limit + 1;

        ListChatMessagesQuery query = cursor.isNull()
                ? ListChatMessagesQuery.firstPage(roomId, limitPlus1)
                : ListChatMessagesQuery.prevPage(roomId, cursor.lastMsgId(), cursor.lastCreatedAtMs(), limitPlus1);

        List<ChatMessage> result = chatMessageQueryUseCase.listMessages(query);

        return ResponseEntity.ok(
                CursorPages.from(result, limit, ChatMessageResponse::fromDomain)
        );
    }
}