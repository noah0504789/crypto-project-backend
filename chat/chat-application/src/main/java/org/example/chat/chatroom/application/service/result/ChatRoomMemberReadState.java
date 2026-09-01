package org.example.chat.chatroom.application.service.result;

/**
 * Mongo membership 이 보관하는 사용자 고유 상태. projection 재생성의 durable source 다.
 */
public record ChatRoomMemberReadState(String memberId, long lastMsgReadSeq) {
}
