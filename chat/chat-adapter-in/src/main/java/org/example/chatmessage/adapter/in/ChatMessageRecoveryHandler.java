//package org.example.chatmessage.adapter.in;
//
//import lombok.RequiredArgsConstructor;
//import org.example.chatmessage.application.port.out.ChatMessageCachePort;
//import org.example.chatmessage.domain.model.ChatMessage;
//import org.example.event.chatmessage.ChatMessagePersistedEvent;
//import org.springframework.stereotype.Component;
//
//@Component
//@RequiredArgsConstructor
//public class ChatMessageRecoveryHandler {
//
//    private final ChatMessageCachePort cache;
//
//    public void handlePersisted(ChatMessagePersistedEvent event) {
//        ChatMessage domain = event.getDomain();
//
//        cache.rollback(domain);
//    }
//}
