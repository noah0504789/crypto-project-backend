package org.example.chatmessage.adapter.in.web;

import lombok.RequiredArgsConstructor;
import org.example.chatmessage.adapter.in.dto.ChatMessageResponse;
import org.example.chat.common.dto.CursorPage;
import org.example.chatmessage.application.dto.ChatMessageCursor;
import org.example.chatmessage.application.port.in.ChatMessageQueryUsecase;
import org.example.chatmessage.domain.model.ChatMessage;
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

    private final ChatMessageQueryUsecase chatMessageQueryService;

    // ?limit=20
    // ?limit=20&lastId=20&lastMsgCreatedAt=1755771000000
    @GetMapping("${api-path.chat.room-messages:/room/{roomId}/messages}")
    public ResponseEntity<CursorPage<ChatMessageResponse>> cursorRecentChatMessages(
            @PathVariable("roomId") String roomId,
            @ModelAttribute ChatMessageCursor cursor,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit
    ) {
        int limitPlus1 = limit + 1;

        List<ChatMessage> res = cursor.isNull() ?
                chatMessageQueryService.listLatest(roomId, limitPlus1) :
                chatMessageQueryService.listPrev(roomId, cursor.lastId(), cursor.lastCreatedAtMillis(), limitPlus1);

        if (res.isEmpty()) return ResponseEntity.ok(new CursorPage(null, false));

        boolean hasNext = res.size() > limit;
        if (hasNext) res = res.subList(0, limit);

        return ResponseEntity.ok(new CursorPage<>(res.stream().map(ChatMessageResponse::fromDomain).toList(), hasNext));
    }
}
