package org.example.chat.chatroom.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.example.chat.chatroom.adapter.in.web.dto.*;
import org.example.chat.chatroom.application.service.command.ChatRoomActivityCommand;
import org.example.chat.chatroom.application.service.query.GetMyChatRoomQuery;
import org.example.chat.chatroom.application.service.query.ListMyChatRoomsQuery;
import org.example.chat.chatroom.application.service.query.ListPopularChatRoomsQuery;
import org.example.common.dto.CursorPage;
import org.example.chat.chatroom.application.port.in.ChatRoomCommandUseCase;
import org.example.chat.chatroom.application.port.in.ChatRoomQueryUseCase;
import org.example.chat.chatroom.application.service.result.MyChatRoomSummary;
import org.example.chat.chatroom.domain.model.ChatRoom;
import org.example.chat.chatroom.domain.model.ChatRoomCategory;
import org.example.common.dto.CursorPages;
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
import java.util.function.Function;

@RestController
@RequestMapping("${api-path.chat.base:/chat}")
@RequiredArgsConstructor
@Validated
public class ChatRoomController {

    private final ChatRoomQueryUseCase chatRoomQueryUseCase;
    private final ChatRoomCommandUseCase chatRoomCommandUseCase;

    @GetMapping("${api-path.chat.rooms-popular:/rooms/popular}")
    public ResponseEntity<CursorPage<ChatRoomResponse>> popularChatRooms(
            @RequestParam("category") ChatRoomCategory category,
            @RequestParam(value = "limit", defaultValue = "10") Integer limit,
            @ModelAttribute ChatRoomCursor cursor
    ) {
        int limitPlus1 = limit + 1;

        ListPopularChatRoomsQuery query = cursor.isNull()
                ? ListPopularChatRoomsQuery.firstPage(category, limitPlus1)
                : ListPopularChatRoomsQuery.nextPage(category, cursor.lastId(), cursor.lastPopularity(), limitPlus1);

        List<ChatRoom> result = chatRoomQueryUseCase.listPopularRooms(query);

        return ResponseEntity.ok(
                CursorPages.from(result, limit, ChatRoomResponse::fromDomain)
        );
    }

    @GetMapping("${api-path.chat.rooms-me:/rooms/me}")
    public ResponseEntity<CursorPage<MyChatRoomResponse>> myChatRooms(
            @RequestParam(value = "limit", defaultValue = "10") Integer limit,
            @ModelAttribute MyChatRoomCursor cursor,
            @RequestHeader(HttpHeaderKey.USER_ID_VALUE) String myUserId
    ) {
        int limitPlus1 = limit + 1;

        ListMyChatRoomsQuery query = cursor.isNull()
                ? ListMyChatRoomsQuery.firstPage(myUserId, limitPlus1)
                : ListMyChatRoomsQuery.nextPage(myUserId, cursor.lastId(), cursor.lastUnreadFlag(), cursor.lastMsgCreatedAtMs(), limitPlus1);

        List<MyChatRoomSummary> result = chatRoomQueryUseCase.listMyRooms(query);

        return ResponseEntity.ok(
                CursorPages.from(result, limit, MyChatRoomResponse::from)
        );
    }

    @GetMapping("${api-path.chat.room:/room/{roomId}}")
    public ResponseEntity<ChatRoomResponse> chatRoom(
            @PathVariable("roomId") String roomId
    ) {
        ChatRoom chatRoom = chatRoomQueryUseCase.getRoom(roomId);

        return ResponseEntity.ok(ChatRoomResponse.fromDomain(chatRoom));
    }

    @GetMapping("${api-path.chat.room-me:/room/{roomId}/me}")
    public ResponseEntity<MyChatRoomResponse> myChatRoom(
            @PathVariable("roomId") String roomId,
            @RequestHeader(HttpHeaderKey.USER_ID_VALUE) String myUserId
    ) {
        MyChatRoomSummary summary = chatRoomQueryUseCase.getMyRoom(
                GetMyChatRoomQuery.of(roomId, myUserId)
        );

        return ResponseEntity.ok(MyChatRoomResponse.from(summary));
    }

    @PostMapping("${api-path.chat.room-members:/room/{roomId}/members}")
    public ResponseEntity<Void> join(
            @PathVariable("roomId") String roomId,
            @RequestHeader(HttpHeaderKey.USER_ID_VALUE) String myUserId) {
        boolean isNewMember = chatRoomCommandUseCase.join(roomId, myUserId);

        return isNewMember ?
                ResponseEntity.created(URI.create(String.format("/chat/room/%s/member/%s", roomId, myUserId))).build() :
                ResponseEntity.noContent().build();
    }

    @DeleteMapping("${api-path.chat.room-members:/room/{roomId}/members}")
    public ResponseEntity<Void> leave(
            @PathVariable("roomId") String roomId,
            @RequestHeader(HttpHeaderKey.USER_ID_VALUE) String myUserId) {
        chatRoomCommandUseCase.leave(roomId, myUserId);

        return ResponseEntity.noContent().build();
    }

    @PutMapping("${api-path.chat.room-activity:/room/{roomId}/activity}")
    public ResponseEntity<Void> activity(
            @PathVariable("roomId") String roomId,
            @RequestParam("lastMsgSeq") Long lastMsgSeq,
            @RequestParam("lastMsgMs") Long lastMsgMs,
            @RequestHeader(HttpHeaderKey.USER_ID_VALUE) String myUserId) {
        chatRoomCommandUseCase.activity(new ChatRoomActivityCommand(roomId, myUserId, lastMsgSeq, lastMsgMs));

        return ResponseEntity.noContent().build();
    }

    // TODO: 여기 아래로부터 인가 처리하기
    @PostMapping("${api-path.chat.room-create:/room}")
    public ResponseEntity<Void> create(
            @RequestBody @Valid ChatRoomCreateRequest request,
            @RequestHeader(HttpHeaderKey.USER_ID_VALUE) String hostId
    ) {
        chatRoomCommandUseCase.create(request.toCommand(hostId));

        return ResponseEntity.created(URI.create("/home")).build();
    }

    @PatchMapping("${api-path.chat.room:/room/{roomId}}")
    public ResponseEntity<Void> update(
            @PathVariable("roomId") @NotBlank String roomId,
            @RequestBody @Valid ChatRoomUpdateRequest request
    ) {
        if (request.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        chatRoomCommandUseCase.update(request.toCommand(roomId));

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("${api-path.chat.room:/room/{roomId}}")
    public ResponseEntity<Void> delete(@PathVariable("roomId") String roomId) {
        chatRoomCommandUseCase.delete(roomId);

        return ResponseEntity.noContent().build();
    }
}
