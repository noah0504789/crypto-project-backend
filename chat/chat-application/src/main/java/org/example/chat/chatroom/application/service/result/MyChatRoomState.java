package org.example.chat.chatroom.application.service.result;

import org.example.chat.chatroom.domain.model.ChatRoom;

/**
 * 한 사용자의 방 하나에 대한 durable 상태. 방 watermark 는 {@code room} 이, 읽음 위치는
 * membership 이 갖는다. 정렬 점수는 이 둘로 application 이 계산한다.
 */
public record MyChatRoomState(ChatRoom room, long lastMsgReadSeq) {
}
