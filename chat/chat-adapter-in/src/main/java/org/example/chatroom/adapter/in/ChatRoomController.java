package org.example.chatroom.adapter.in;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.example.chatmessage.adapter.dto.CursorPage;
import org.example.chatroom.adapter.dto.*;
import org.example.chatroom.application.port.in.ChatRoomCommandUseCase;
import org.example.chatroom.application.port.in.ChatRoomQueryUseCase;
import org.example.chatroom.domain.model.ChatRoom;
import org.example.chatroom.domain.model.ChatRoomCategory;
import org.example.common.enums.HttpHeaderKey;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("${api-path.chat.base:/chat}")
@RequiredArgsConstructor
@Validated
public class ChatRoomController {

    private final ChatRoomCommandUseCase chatRoomCommandUseCase;
    private final ChatRoomQueryUseCase chatRoomQueryUseCase;

    @GetMapping("${api-path.chat.rooms-popular:/rooms/popular}")
    public ResponseEntity<CursorPage<ChatRoomResponse>> popularChatRooms(
            @RequestParam("category") ChatRoomCategory category,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit,
            @ModelAttribute ChatRoomCursor cursor) {
        int limitPlus1 = limit + 1;

        List<ChatRoom> result = cursor.isNull() ?
                chatRoomQueryUseCase.listMostPopular(category, limitPlus1) :
                chatRoomQueryUseCase.listNextPopular(category, cursor.lastId(), cursor.lastPopularity(), limitPlus1);

        if (result.isEmpty()) return ResponseEntity.ok(new CursorPage(null, false));

        boolean hasNext = result.size() > limit;
        if (hasNext) result = result.subList(0, limit);

        return ResponseEntity.ok(new CursorPage<>(result.stream().map(ChatRoomResponse::fromDomain).toList(), hasNext));
    }

    @GetMapping("${api-path.chat.rooms-me:/rooms/me}")
    public ResponseEntity<CursorPage<MyChatRoomResponse>> myChatRooms(
            @RequestParam(value = "limit", defaultValue = "10") Integer limit,
            @ModelAttribute MyChatRoomCursor cursor,
            @RequestHeader(HttpHeaderKey.USER_ID_VALUE) String myUserId) {
        int limitPlus1 = limit + 1;

        List<MyChatRoomResponse> result = cursor.isNull() ?
                chatRoomQueryUseCase.listLatestActive(myUserId, limitPlus1) :
                chatRoomQueryUseCase.listActiveBefore(myUserId, cursor.lastId(), cursor.lastUnreadFlag(), cursor.lastMsgCreatedAt().toEpochMilli(), limitPlus1);

        if (result.isEmpty()) return ResponseEntity.ok(new CursorPage(null, false));

        boolean hasNext = result.size() > limit;
        if (hasNext) result = result.subList(0, limit);

        return ResponseEntity.ok(new CursorPage<>(result, hasNext));
    }

    @GetMapping("${api-path.chat.room:/room/{roomId}}")
    public ResponseEntity<ChatRoomResponse> chatRoom(@PathVariable("roomId") String roomId) {
        ChatRoom chatRoom = chatRoomQueryUseCase.findById(roomId);

        return ResponseEntity.ok(ChatRoomResponse.fromDomain(chatRoom));
    }

    @GetMapping("${api-path.chat.room-me:/room/{roomId}/me}")
    public ResponseEntity<MyChatRoomResponse> myChatRoom(
            @PathVariable("roomId") String roomId,
            @RequestHeader(HttpHeaderKey.USER_ID_VALUE) String myUserId) {
        return ResponseEntity.ok(chatRoomQueryUseCase.findActive(roomId, myUserId));
    }

    @PostMapping("${api-path.chat.room-members:/room/{roomId}/members}")
    public ResponseEntity join(
            @PathVariable("roomId") String roomId,
            @RequestHeader(HttpHeaderKey.USER_ID_VALUE) String myUserId) {
        Boolean isNewMember = chatRoomCommandUseCase.join(roomId, myUserId);

        return isNewMember ?
                ResponseEntity.created(URI.create(String.format("/chat/room/%s/member/%s", roomId, myUserId))).build() :
                ResponseEntity.noContent().build();
    }

    @DeleteMapping("${api-path.chat.room-members:/room/{roomId}/members}")
    public ResponseEntity leave(
            @PathVariable("roomId") String roomId,
            @RequestHeader(HttpHeaderKey.USER_ID_VALUE) String myUserId) {
        chatRoomCommandUseCase.leave(roomId, myUserId);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("${api-path.chat.room-activity:/room/{roomId}/activity}")
    public ResponseEntity activity(
            @PathVariable("roomId") String roomId,
            @RequestParam("lastMsgSeq") Long lastMsgSeq,
            @RequestParam("lastMsgMs") Long lastMsgMs,
            @RequestHeader(HttpHeaderKey.USER_ID_VALUE) String myUserId) {
        chatRoomCommandUseCase.activity(roomId, myUserId, lastMsgSeq, lastMsgMs);

        return ResponseEntity.noContent().build();
    }

    // TODO: 여기 아래로부터 인가 처리하기

    @PostMapping("${api-path.chat.room-create:/room}")
    public ResponseEntity<Void> create(
            @RequestBody @Valid ChatRoomCreateRequest request,
            @RequestHeader(HttpHeaderKey.USER_ID_VALUE) String hostId
    ) {
        chatRoomCommandUseCase.save(hostId, request);

        return ResponseEntity.created(URI.create("/home")).build();
    }

    @PatchMapping("${api-path.chat.room:/room/{roomId}}")
    public ResponseEntity<Void> update(
            @PathVariable("roomId") @NotBlank String roomId,
            @RequestBody @Valid ChatRoomUpdateRequest request
    ) {
        Map<String, Object> updated = request.toUpdateMap();

        if (updated.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        chatRoomCommandUseCase.update(roomId, updated);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("${api-path.chat.room:/room/{roomId}}")
    public ResponseEntity delete(@PathVariable("roomId") String roomId) {
        chatRoomCommandUseCase.delete(roomId);

        return ResponseEntity.noContent().build();
    }
}
