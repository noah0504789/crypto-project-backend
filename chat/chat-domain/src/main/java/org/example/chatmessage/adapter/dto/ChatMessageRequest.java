//package org.example.chatmessage.adapter.dto;
//
//import org.example.chatmessage.domain.model.ChatMessage;
//
//public record ChatMessageRequest(
//        String roomId,
//        Long writerId, // TODO: 로그인 계정 아이디로
//        String content
//        // TODO: requestId (멱등성)
//) {
//    public ChatMessage toDomain() {
//        return new ChatMessage(roomId, writerId, content);
//    }
//}
