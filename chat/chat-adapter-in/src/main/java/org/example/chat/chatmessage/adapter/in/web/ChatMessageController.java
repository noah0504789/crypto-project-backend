package org.example.chat.chatmessage.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.example.chat.chatmessage.adapter.in.web.dto.ChatMessageResponse;
import org.example.chat.chatmessage.application.service.query.ListChatMessagesQuery;
import org.example.common.dto.CursorPage;
import org.example.chat.chatmessage.adapter.in.web.dto.ChatMessageCursor;
import org.example.chat.chatmessage.application.port.in.ChatMessageQueryUseCase;
import org.example.chat.chatmessage.domain.model.ChatMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.Function;

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
                : ListChatMessagesQuery.prevPage(roomId, cursor.lastId(), cursor.lastCreatedAtMillis(), limitPlus1);

        List<ChatMessage> result = chatMessageQueryUseCase.listMessages(query);

        return toCursorPage(result, limit, ChatMessageResponse::fromDomain);
    }

    private <T, R> ResponseEntity<CursorPage<R>> toCursorPage(
            List<T> result,
            int limit,
            Function<T, R> mapper
    ) {
        if (result.isEmpty()) {
            return ResponseEntity.ok(new CursorPage<>(null, false));
        }

        boolean hasNext = result.size() > limit;

        List<T> pageItems = hasNext
                ? result.subList(0, limit)
                : result;

        return ResponseEntity.ok(
                new CursorPage<>(
                        pageItems.stream()
                                .map(mapper)
                                .toList(),
                        hasNext
                )
        );
    }
}